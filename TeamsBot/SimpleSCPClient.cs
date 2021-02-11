// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Threading.Tasks;
using Microsoft.Extensions.Configuration;
using Newtonsoft.Json.Linq;

namespace Microsoft.BotBuilderSamples
{
    /// <summary>
    /// Simplifies service consumption from SAP Cloud Platform (SCP)
    /// including user authentication with principal propagation based on 
    /// the OAuth 2.0 SAML Bearer Assertion Grant type.
    /// </summary>
    public class SimpleSCPClient
    {
        // V1 AAD path for OBO flow
        private const string V1_PATH_OAUTH = "/{0}/oauth2/token";
        // JWT-Bearer token grant type
        private const string GRANT_TYPE_JWT_BEARER = "urn:ietf:params:oauth:grant-type:jwt-bearer";
        // On behalf of token use
        private const string REQUESTED_TOKEN_USE_VALUE = "on_behalf_of";
        // Token type SAM2
        private const string REQUESTED_TOKEN_TYPE_SAML = "urn:ietf:params:oauth:token-type:saml2";
        // Token type SAML bearer
        private const string GRANT_TYPE_SAML_BEARER = "urn:ietf:params:oauth:grant-type:saml2-bearer";
        // XSUAA hostname
        private const string XSUAA_BASEURI = "https://{0}.authentication.{1}.hana.ondemand.com";
        // AAD hostname
        private const string AAD_BASEURI = "https://login.microsoftonline.com";
        // XSUAA token endpoint URL
        private const string XSUAA_ACS_PATH = "/oauth/token/alias/{0}.{1}";
        private const string HEADER_URL_ENCODED = "application/x-www-form-urlencoded";


        private readonly string _appId;
        private readonly string _appSecret;
        private readonly string _scpLandscape;
        private readonly string _xsuaaACSURLSuffix;
        private readonly string _scpAccountName;
        private readonly string _aadTenantId;
        private readonly string _xsuaaClientId;
        private readonly string _xsuaaSecret;

        public SimpleSCPClient(IConfiguration configuration)
        {
            _appId = configuration["MicrosoftAppId"];
            _appSecret = configuration["MicrosoftAppPassword"];
            _scpLandscape = configuration["SCPLandscape"];
            _xsuaaACSURLSuffix = configuration["XSUAAACSURLSuffix"];
            _scpAccountName = configuration["SCPAccountName"];
            _aadTenantId = configuration["AADTenantId"];
            _xsuaaClientId = configuration["XSUAAClientId"];
            _xsuaaSecret = configuration["XSUAASecret"];
        }

        public async Task<string> GetSAMLAssertionFromAAD(String aadAccessToken) {
            using (HttpClient httpClient = new HttpClient())
            {
                httpClient.BaseAddress = new Uri(AAD_BASEURI);
                httpClient.DefaultRequestHeaders.Accept.Clear();

                var content = new FormUrlEncodedContent(new[]
                {
                    new KeyValuePair<string, string>("assertion", aadAccessToken),
                    new KeyValuePair<string, string>("grant_type", GRANT_TYPE_JWT_BEARER),
                    new KeyValuePair<string, string>("client_id", _appId),
                    new KeyValuePair<string, string>("client_secret", _appSecret),
                    new KeyValuePair<string, string>("resource", String.Format(XSUAA_BASEURI, _scpAccountName, _scpLandscape)),
                    new KeyValuePair<string, string>("requested_token_use", REQUESTED_TOKEN_USE_VALUE),
                    new KeyValuePair<string, string>("requested_token_type", REQUESTED_TOKEN_TYPE_SAML)
                });

                content.Headers.ContentType = new MediaTypeHeaderValue(HEADER_URL_ENCODED);
              
                string aadTokenEndpoint = String.Format(V1_PATH_OAUTH, _aadTenantId);
                var httpResponse = await httpClient.PostAsync(aadTokenEndpoint, content);

                httpResponse.EnsureSuccessStatusCode(); // throws if not 200-299

                if (httpResponse.Content is object && httpResponse.Content.Headers.ContentType.MediaType == "application/json")
                {
                    var responseBody = httpResponse.Content.ReadAsStringAsync().Result;
                    var samlAssertion = "";
                    try
                    {
                        // var jsonResponse = await JsonSerializer.DeserializeAsync<JsonDocument>(contentStream, new System.Text.Json.JsonSerializerOptions { IgnoreNullValues = true, PropertyNameCaseInsensitive = true });
                        var jsonResponse = JObject.Parse(responseBody);
                        samlAssertion = jsonResponse.GetValue("access_token").ToString();
                        // samlAssertion = jsonResponse.RootElement.GetProperty("access_token").GetString();
                        return samlAssertion;
                    }
                    catch (Newtonsoft.Json.JsonException) // Invalid JSON
                    {
                        throw new InvalidOperationException("No JSON response. SAML Token request failed");
                    }                
                }
                else
                {
                    throw new InvalidOperationException("HTTP Response was invalid and cannot be deserialized.");
                }
            }
        }    


        public async Task<string> GetAccessTokenFromSCP(String samlAssertion) {
            using (HttpClient httpClient = new HttpClient())
            {
                httpClient.BaseAddress = new Uri(String.Format(XSUAA_BASEURI, _scpAccountName, _scpLandscape));
                httpClient.DefaultRequestHeaders.Accept.Clear();
                var authHeader = Encoding.ASCII.GetBytes($"{_xsuaaClientId}:{_xsuaaSecret}");
                httpClient.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Basic", Convert.ToBase64String(authHeader));
                
                var content = new FormUrlEncodedContent(new[]
                {
                    new KeyValuePair<string, string>("assertion", samlAssertion),
                    new KeyValuePair<string, string>("grant_type", GRANT_TYPE_SAML_BEARER)                    
                });

                content.Headers.ContentType = new MediaTypeHeaderValue(HEADER_URL_ENCODED);
              
                string scpTokenEndpoint = String.Format(XSUAA_ACS_PATH, _scpAccountName, _xsuaaACSURLSuffix);
                var httpResponse = await httpClient.PostAsync(scpTokenEndpoint, content);

                httpResponse.EnsureSuccessStatusCode(); // throws if not 200-299

                if (httpResponse.Content is object && httpResponse.Content.Headers.ContentType.MediaType == "application/json")
                {
                    var responseBody = httpResponse.Content.ReadAsStringAsync().Result;
                    var accessToken = "";
                    try
                    {
                        var jsonResponse = JObject.Parse(responseBody);
                        accessToken = jsonResponse.GetValue("access_token").ToString();
                        return accessToken;
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

        public async Task<string> GetProductData(String xsuaaAccessToken) {
            using (HttpClient httpClient = new HttpClient())
            {
                httpClient.BaseAddress = new Uri(String.Format(XSUAA_BASEURI, _scpAccountName, _scpLandscape));
                httpClient.DefaultRequestHeaders.Accept.Clear();
                var authHeader = Encoding.ASCII.GetBytes($"{_xsuaaClientId}:{_xsuaaSecret}");
                httpClient.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Basic", Convert.ToBase64String(authHeader));
                
                var content = new FormUrlEncodedContent(new[]
                {
                    new KeyValuePair<string, string>("assertion", xsuaaAccessToken),
                    new KeyValuePair<string, string>("grant_type", GRANT_TYPE_SAML_BEARER)                    
                });

                content.Headers.ContentType = new MediaTypeHeaderValue(HEADER_URL_ENCODED);
              
                string scpTokenEndpoint = String.Format(XSUAA_ACS_PATH, _scpAccountName, _xsuaaACSURLSuffix);
                var httpResponse = await httpClient.PostAsync(scpTokenEndpoint, content);

                httpResponse.EnsureSuccessStatusCode(); // throws if not 200-299

                if (httpResponse.Content is object && httpResponse.Content.Headers.ContentType.MediaType == "application/json")
                {
                    var responseBody = httpResponse.Content.ReadAsStringAsync().Result;
                    var accessToken = "";
                    try
                    {
                        var jsonResponse = JObject.Parse(responseBody);
                        accessToken = jsonResponse.GetValue("access_token").ToString();
                        return accessToken;
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
    }
}