// vue.config.js
const isProduction = process.env.NODE_ENV === "production";
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
    configureWebpack: {
      devtool: 'inline-source-map'
    }
  }
}

module.exports = config;
