// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

const https = require('https');
const { CardFactory, AttachmentLayoutTypes } = require('botbuilder');
const { ConfirmPrompt, DialogSet, DialogTurnStatus, OAuthPrompt, TextPrompt, WaterfallDialog } = require('botbuilder-dialogs');

const { LogoutDialog } = require('./logoutDialog');
const { OAuthSAMLHelper } = require('../oauthSAMLHelper');

const CONFIRM_PROMPT = 'ConfirmPrompt';
const MAIN_DIALOG = 'MainDialog';
const MAIN_WATERFALL_DIALOG = 'MainWaterfallDialog';
const OAUTH_PROMPT = 'OAuthPrompt';
const PRODUCT_PROMPT = 'ProductPrompt';

class MainDialog extends LogoutDialog {
    constructor() {
        super(MAIN_DIALOG, process.env.connectionName);

        this.addDialog(new OAuthPrompt(OAUTH_PROMPT, {
            connectionName: process.env.connectionName,
            text: 'Please Sign In',
            title: 'Sign In',
            timeout: 300000
        }));
        this.addDialog(new TextPrompt(PRODUCT_PROMPT));
        this.addDialog(new ConfirmPrompt(CONFIRM_PROMPT));
        this.addDialog(new WaterfallDialog(MAIN_WATERFALL_DIALOG, [
            this.promptStep.bind(this),
            this.loginStep.bind(this),
            this.displayTokenPhase1.bind(this),
            this.displayTokenPhase2.bind(this),
            this.productSearch.bind(this)
        ]));

        this.initialDialogId = MAIN_WATERFALL_DIALOG;
    }

    /**
     * The run method handles the incoming activity (in the form of a DialogContext) and passes it through the dialog system.
     * If no dialog is active, it will start the default dialog.
     * @param {*} dialogContext
     */
    async run(context, accessor) {
        const dialogSet = new DialogSet(accessor);
        dialogSet.add(this);

        const dialogContext = await dialogSet.createContext(context);
        const results = await dialogContext.continueDialog();
        if (results.status === DialogTurnStatus.empty) {
            await dialogContext.beginDialog(this.id);
        }
    }

    async promptStep(stepContext) {
        return await stepContext.beginDialog(OAUTH_PROMPT);
    }

    async loginStep(stepContext) {
        // Get the token from the previous step. Note that we could also have gotten the
        // token directly from the prompt itself. There is an example of this in the next method.
        const tokenResponse = stepContext.result;
        if (tokenResponse) {
            return await stepContext.prompt(CONFIRM_PROMPT, 'Would you like to view your token?');
        }
        await stepContext.context.sendActivity('Login was not successful please try again.');
        return await stepContext.endDialog();
    }

    async displayTokenPhase1(stepContext) {
        await stepContext.context.sendActivity('Thank you.');

        const result = stepContext.result;
        if (result) {
            // Call the prompt again because we need the token. The reasons for this are:
            // 1. If the user is already logged in we do not need to store the token locally in the bot and worry
            // about refreshing it. We can always just call the prompt again to get the token.
            // 2. We never know how long it will take a user to respond. By the time the
            // user responds the token may have expired. The user would then be prompted to login again.
            //
            // There is no reason to store the token locally in the bot because we can always just call
            // the OAuth prompt to get the token or get a new token if needed.
            return await stepContext.beginDialog(OAUTH_PROMPT);
        }
        return await stepContext.endDialog();
    }

    async displayTokenPhase2(stepContext) {
        const aadTokenResponse = stepContext.result;
        if (aadTokenResponse) {
            await stepContext.context.sendActivity(`Here is your OAuth access token ${ aadTokenResponse.token }`);
            stepContext.values.token = aadTokenResponse;
            return await stepContext.prompt(PRODUCT_PROMPT, { prompt: 'Which product are you looking for?' }); 
        }
        return await stepContext.endDialog();
    }

    async productSearch(stepContext) {
        if (stepContext.result) {            
            var products = null;
            const aadTokenResponse = stepContext.values.token;
            if (aadTokenResponse && aadTokenResponse.token) {
                var oauthSAMLHelper = new OAuthSAMLHelper();
                var xsuaaAccessToken = await oauthSAMLHelper.getSAMLAssertionFromAAD(aadTokenResponse.token).then(oauthSAMLHelper.getAccessTokenFromXSUAA);

                var productResultSet = new Promise(function(resolve, reject) {
                    console.log("Now calling iFlow with access token " + xsuaaAccessToken);
                    https.get({
                        protocol: 'https:',
                        hostname: process.env.cpiHost,
                        port: 443,                       
                        path: process.env.cpiPath,
                        headers: {                   
                            'Authorization': 'Bearer ' + xsuaaAccessToken,
                            'productId' : stepContext.result
                        }
                    },                    
                    (response) => {
                        let body = '';
                        response.on('data', function(data) {
                            body += String(data);
                        });
    
                        response.on('end', function() {
                            // console.log(body);
                            // Success finally return the resultset
                            resolve(JSON.parse(body));
                        });
                    }).on('error', (err) => {
                        console.log('Error: ' + err.message);
                        reject(new Error('Error: ' + err.message));
                    });
                });                
                
                products = await productResultSet;                
                const numberOfProducts = products.d.results.length;

                // Get the images from the public SAP E5 system
                var imageURL = 'https://sapes5.sapdevcenter.com';

                // Create a hero card and loop over products result set
                // https://docs.microsoft.com/en-us/adaptive-cards/
                const reply = { attachments: [], attachmentLayout: AttachmentLayoutTypes.List };
                for (let cnt = 0; cnt < numberOfProducts; cnt++) {
                    var product = products.d.results[cnt];
                    var productImageURL = imageURL + product.ProductPictureURL;
                    const card = CardFactory.heroCard(
                        'Product Name: ' + product.Product,
                        'Category: ' + product.ProductCategory,
                        [{ type: 'Image', alt: 'SAP Logo', url: productImageURL, height: '5px', width: '5px' }],
                        ['Order via email'],
                        { subtitle: `Price : ${ product.Price } ${ product.Currency }` }
                    );
                    reply.attachments.push(card);
                }                
                await stepContext.context.sendActivity(reply);                
            } else {
                await stepContext.context.sendActivity('We couldn\'t log you in. Please try again later.');
            }
        }
        return await stepContext.endDialog();   
    }    
}

module.exports.MainDialog = MainDialog;
