const base=require('./builder.local-test.cjs');
module.exports={...base,compression:"store",appId:'com.aden.desktop.collectiontest',productName:'Aden Collection Test',extraMetadata:{name:'aden-collection-test'},directories:{output:'dist/collection-delivery'},nsis:{...base.nsis,runAfterFinish:false,createDesktopShortcut:false,artifactName:'Aden-Collection-Test-${version}-Setup.${ext}'}};



