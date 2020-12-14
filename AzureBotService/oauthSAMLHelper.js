// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

var https = require('https');
var querystring = require('querystring');

// V1 AAD path for OBO flow
const V1_PATH_OAUTH = '/%AADTenantID%/oauth2/token';
// JWT-Bearer token grant type
const GRANT_TYPE_JWT_BEARER = 'urn:ietf:params:oauth:grant-type:jwt-bearer';
// On behalf of token use
const REQUESTED_TOKEN_USE_VALUE = 'on_behalf_of';
// Token type SAM2
const REQUESTED_TOKEN_TYPE_SAML = 'urn:ietf:params:oauth:token-type:saml2';
// Token type SAML bearer
const GRANT_TYPE_SAML_BEARER = 'urn:ietf:params:oauth:grant-type:saml2-bearer';
// XSUAA hostname
const XSUAA_HOST = '%SCPAccountName%.authentication.%SCPLandscape%.hana.ondemand.com';
// AAD hostname
const AAD_HOST = 'login.microsoftonline.com'
// XSUAA token endpoint URL
const XSUAA_ACS_PATH = '/oauth/token/alias/%SCPAccountName%.%XSUAAACSURLSuffix%'
const HEADER_URL_ENCODED = 'application/x-www-form-urlencoded';

class OAuthSAMLHelper {
    async getSAMLAssertionFromAAD(accessToken) {
        console.log('Requesting SAML assertion')
        var aadTokenEndpoint = V1_PATH_OAUTH.replace('%AADTenantID%', process.env.AADTenantID)
        var resource = 'https://' + XSUAA_HOST.replace('%SCPAccountName%', process.env.scpAccountName).replace('%SCPLandscape%', process.env.scpLandscape);
        var postData = querystring.stringify({           
            assertion: accessToken,            
            grant_type: GRANT_TYPE_JWT_BEARER,            
            client_id: process.env.chatBotAppId,            
            client_secret: process.env.chatBotSecret,           
            resource: resource,        
            requested_token_use: REQUESTED_TOKEN_USE_VALUE,            
            requested_token_type: REQUESTED_TOKEN_TYPE_SAML
        });

        var aadSAMLAssertion = new Promise(function(resolve, reject) {
            console.log('Sending request to AAD token endpoint (V1)');
            var options = {
                host: AAD_HOST,
                port: 443,
                method: 'POST',
                path: aadTokenEndpoint,
                headers: {
                    'Content-Type': HEADER_URL_ENCODED,
                    'Content-Length': postData.length
                }
            };
            
            var req = https.request(options, function(res) {
                var result = '';

                res.on('data', function(chunk) {
                    result += chunk;
                });
                res.on('end', function() {
                    console.log(result);
                    var aadResponse = JSON.parse(result);
                    if (aadResponse.access_token) {
                        // Success and we only return the access_token from JSON
                        resolve(aadResponse.access_token);
                    } else {
                        var errorMessage = '';
                        if (aadResponse.error) {
                            errorMessage = aadResponse.error + ": " + aadResponse.error_description;
                        } else {
                            errorMessage = "No SAML assertion returned";
                        }
                        reject(new Error(errorMessage));
                    }
                });
                res.on('error', function(err) {
                    console.log(err);
                    reject(new Error(err));
                });
            });

            // req error
            req.on('error', function(err) {
                console.log(err);
            });

            // send request with the postData form
            req.write(postData);
            req.end();
        });

        var result = await aadSAMLAssertion;
        return result;
    }

    async getAccessTokenFromXSUAA(samlAssertionBase64) {        
        console.log('Request access token from XSUAA with SAML assertion ' + new Buffer.from(samlAssertionBase64, 'base64').toString('ascii'));
        var xsuaaACSHost = XSUAA_HOST.replace('%SCPAccountName%', process.env.scpAccountName).replace('%SCPLandscape%', process.env.scpLandscape);
        var xsuaaACSPath = XSUAA_ACS_PATH.replace('%SCPAccountName%', process.env.scpAccountName).replace('%XSUAAACSURLSuffix%', process.env.xsuaaACSURLSuffix);
        var postData = querystring.stringify({           
            assertion: samlAssertionBase64,            
            grant_type: GRANT_TYPE_SAML_BEARER
        });
        var clientCredAuthHeader = 'Basic ' + new Buffer.from( process.env.xsuaaClientId + ':' +  process.env.xsuaaSecret).toString('base64');

        var xsuaaAccessToken = new Promise(function(resolve, reject) {
            console.log('Sending request to XSUAA token endpoint');
            var options = {
                host: xsuaaACSHost,
                port: 443,
                method: 'POST',
                path: xsuaaACSPath,
                headers: {
                    'Content-Type': HEADER_URL_ENCODED,
                    'Content-Length': postData.length,                    
                    'Authorization': clientCredAuthHeader
                }
            };
            
            var req = https.request(options, function(res) {
                var result = '';

                res.on('data', function(chunk) {
                    result += chunk;
                });
                res.on('end', function() {
                    console.log(result);
                    var xsuaaResponse = JSON.parse(result);
                    if (xsuaaResponse.access_token) {
                        // Success and we only return the access_token from JSON
                        resolve(xsuaaResponse.access_token);
                    } else {
                        var errorMessage = '';
                        if (xsuaaResponse.error) {
                            errorMessage = xsuaaResponse.error + ": " + xsuaaResponse.error_description;
                        } else {
                            errorMessage = "No access token returned";
                        }
                        reject(new Error(errorMessage));
                    }
                });
                res.on('error', function(err) {
                    console.log(err);
                    reject(new Error(err));
                });
            });

            // req error
            req.on('error', function(err) {
                console.log(err);
            });

            // send request with the postData form
            req.write(postData);
            req.end();
        }); 

        var result = await xsuaaAccessToken;
        return result;
    }
}

module.exports.OAuthSAMLHelper = OAuthSAMLHelper;