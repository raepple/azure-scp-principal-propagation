sap.ui.define(
    ["sap/ui/test/Opa5"],
    /**
     * @param {typeof sap.ui.test.Opa5} Opa5
     */
    function (Opa5) {
        "use strict";

        return Opa5.extend("com.microsoft.samples.iasaaddemo.test.integration.arrangements.Startup", {
            iStartMyApp: function () {
                this.iStartMyUIComponent({
                    componentConfig: {
                        name: "com.microsoft.samples.iasaaddemo",
                        async: true,
                        manifest: true,
                    },
                });
            },
        });
    }
);
