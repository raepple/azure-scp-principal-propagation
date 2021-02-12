// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using System.Net.Http;
using System.Net.Http.Headers;
using Microsoft.Bot.Builder;
using Microsoft.Bot.Builder.Dialogs;
using Microsoft.Bot.Schema;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using Newtonsoft.Json.Linq;

namespace Microsoft.BotBuilderSamples
{
    
    public class MainDialog : LogoutDialog
    {
        private readonly ILogger _logger;
        private SCPClient scpClient;
        private readonly string _cpiHost;
        private readonly string _cpiPath;

        public MainDialog(IConfiguration configuration, ILogger<MainDialog> logger)
            : base(nameof(MainDialog), configuration["ConnectionName"])
        {
            _logger = logger;

            scpClient = new SCPClient(configuration);

            _cpiHost = configuration["CPIBaseURI"];
            _cpiPath = configuration["CPIPath"];

            AddDialog(new TokenExchangeOAuthPrompt(
                nameof(TokenExchangeOAuthPrompt),
                new OAuthPromptSettings
                {
                    ConnectionName = ConnectionName,
                    Text = "Please Sign In",
                    Title = "Sign In",
                    Timeout = 1000 * 60 * 1 // User has 5 minutes to login (1000 * 60 * 5)
                    //EndOnInvalidMessage = true
                }));

            AddDialog(new ConfirmPrompt(nameof(ConfirmPrompt)));

            AddDialog(new TextPrompt(nameof(TextPrompt)));

            AddDialog(new WaterfallDialog(nameof(WaterfallDialog),  new WaterfallStep[] { PromptStepAsync, LoginStepAsync, DisplayTokenAsync, AskForProductAsync, DisplaySearchResultsAsync }));

            // The initial child Dialog to run.
            InitialDialogId = nameof(WaterfallDialog);
        }

        private async Task<DialogTurnResult> PromptStepAsync(WaterfallStepContext stepContext, CancellationToken cancellationToken)
        {
            return await stepContext.BeginDialogAsync(nameof(TokenExchangeOAuthPrompt), null, cancellationToken);
        }

        private async Task<DialogTurnResult> LoginStepAsync(WaterfallStepContext stepContext, CancellationToken cancellationToken)
        {
            // Get the token from the previous step. Note that we could also have gotten the
            // token directly from the prompt itself. There is an example of this in the next method.
            var tokenResponse = (TokenResponse)stepContext.Result;
            if (tokenResponse?.Token != null)
            {
                // store token for later use
                stepContext.Values.Add("aadToken", tokenResponse.Token);
                await stepContext.Context.SendActivityAsync($"You're logged in");
                return await stepContext.PromptAsync(nameof(ConfirmPrompt), new PromptOptions { Prompt = MessageFactory.Text("Would you like to view your token?") }, cancellationToken);
            }
            await stepContext.Context.SendActivityAsync(MessageFactory.Text("Login was not successful please try again."), cancellationToken);
            return await stepContext.EndDialogAsync(cancellationToken: cancellationToken);
        }

        private async Task<DialogTurnResult> DisplayTokenAsync(WaterfallStepContext stepContext, CancellationToken cancellationToken)
        {   
            var result = (bool)stepContext.Result;
            if (result) {
                await stepContext.Context.SendActivityAsync(MessageFactory.Text($"Here is your token {stepContext.Values["aadToken"].ToString()}"), cancellationToken);
            }
            return await stepContext.ContinueDialogAsync(cancellationToken: cancellationToken);
        }

        private async Task<DialogTurnResult> AskForProductAsync(WaterfallStepContext stepContext, CancellationToken cancellationToken)
        {
            return await stepContext.PromptAsync(nameof(TextPrompt), new PromptOptions{ Prompt = MessageFactory.Text("Which product are you looking for?") }, cancellationToken);
        }

        private async Task<DialogTurnResult> DisplaySearchResultsAsync(WaterfallStepContext stepContext, CancellationToken cancellationToken)
        {
            if (stepContext.Result != null) {  
                _logger.LogInformation("Search started for product name: " + stepContext.Result);
                string aadAccessToken = stepContext.Values["aadToken"].ToString();
                _logger.LogInformation("Using AAD OAuth token: " + aadAccessToken);
                var samlAssertion = await scpClient.GetSAMLAssertionFromAAD(aadAccessToken);
                _logger.LogInformation("Received SAML assertion from AAD: " + samlAssertion);
                var scpAccessToken = await scpClient.GetAccessTokenFromSCP(samlAssertion);
                _logger.LogInformation("Received access token from SCP: " + scpAccessToken);
                
                using (HttpClient httpClient = new HttpClient())
                {
                    httpClient.BaseAddress = new Uri(_cpiHost);
                    httpClient.DefaultRequestHeaders.Accept.Clear();
                    httpClient.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", scpAccessToken);
                    httpClient.DefaultRequestHeaders.Add("productId", stepContext.Result.ToString());
                    var httpResponse = await httpClient.GetAsync(_cpiPath);

                    httpResponse.EnsureSuccessStatusCode(); // throws if not 200-299

                    if (httpResponse.Content is object && httpResponse.Content.Headers.ContentType.MediaType == "application/json")
                    {
                        var responseBody = httpResponse.Content.ReadAsStringAsync().Result;
                        try
                        {
                            var responseJSON = JObject.Parse(responseBody);
		                    var products = responseJSON.SelectTokens("d.results[*]");
                            var imageURL = "https://sapes5.sapdevcenter.com";
                            var attachments = new List<Attachment>();
                            foreach (var product in products)
                            {
                                var title = String.Format("{0} ({1} {2})", product["Product"], product["Price"], product["Currency"]);
                                var subtitle = product["ProductCategory"].ToString();
                                var imageUrl = imageURL + product["ProductPictureURL"];

                                CardAction orderButton = new CardAction()
                                {
                                    Value = "https://www.microsoft.com/",
                                    Type = ActionTypes.OpenUrl,
                                    Title = "Order now"
                                };

                                var heroCard = new HeroCard
                                {  
                                    Title = title,
                                    Subtitle = subtitle,
                                    Images = new List<CardImage> { new CardImage(imageUrl) },
                                    Buttons =  new List<CardAction> { orderButton }
                                };
                                attachments.Add(heroCard.ToAttachment());
                            }

                            if (attachments.Count > 0) {
                                var activity = MessageFactory.Attachment(attachments);
                                await stepContext.Context.SendActivityAsync(activity, cancellationToken);
                            } else {
                                await stepContext.Context.SendActivityAsync(MessageFactory.Text("Sorry, no products found!"), cancellationToken);
                            }
                        }                                 
                        catch (Newtonsoft.Json.JsonException) // Invalid JSON
                        {
                            throw new InvalidOperationException("No JSON response. Access Token request failed");
                        }  
                    }
                    else
                    {
                        throw new InvalidOperationException("HTTP Response was invalid and cannot be deserialized.");
                    }
                }
            }
            return await stepContext.EndDialogAsync(cancellationToken: cancellationToken);
        }
    }
}
