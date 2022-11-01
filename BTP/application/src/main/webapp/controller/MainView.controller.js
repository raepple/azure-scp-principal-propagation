sap.ui.define(
    ["./BaseController", 
    "sap/ui/model/json/JSONModel"],
    /**
     * @param {typeof sap.ui.core.mvc.Controller} Controller
     */
    function (Controller, JSONModel) {
        "use strict";

        return Controller.extend("com.microsoft.samples.btpgraph.controller.MainView", {
            onInit: function () {
                var oModel = new sap.ui.model.json.JSONModel();
                oModel.loadData("https://approuter-btpgraph-<ID>.cfapps.<region>.hana.ondemand.com/calendar");
                this.getView().setModel(oModel, "events");
            }
        });
    }
);
