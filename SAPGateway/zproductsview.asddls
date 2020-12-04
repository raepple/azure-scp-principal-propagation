@AbapCatalog.sqlViewName: 'ZPRVIEW'
@AbapCatalog.compiler.compareFilter: true
@AbapCatalog.preserveKey: true
@AccessControl.authorizationCheck: #CHECK
@EndUserText.label: 'Product view'
@OData.publish: true
define view ZPRODUCTSVIEW as select from SEPM_I_Product {
    key ProductUUID,
    Product,
    ProductType,
    ProductCategory,    
    Price,
    Currency,
    Height,
    Width,
    Depth,
    DimensionUnit,
    ProductPictureURL,    
    SupplierUUID,
    Weight,
    WeightUnit
}
