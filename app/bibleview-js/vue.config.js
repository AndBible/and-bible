// vue.config.js
const isProduction = process.env.NODE_ENV === "production";

console.log("vue.config.js - isProduction: ", isProduction);

let config = {
  lintOnSave: false,
  runtimeCompiler: true,
  transpileDependencies: ["dom-highlight-range"],
  publicPath: "",
  pluginOptions: {
    webpackBundleAnalyzer: {
      openAnalyzer: false,
      analyzerMode: "disabled",
    }
  },
}

if(isProduction) {
  config.productionSourceMap = false;
} else {
  config = {
    ...config,
    productionSourceMap: true,
    configureWebpack: {
      devtool: 'inline-source-map'
    }
  }
}

module.exports = config;
