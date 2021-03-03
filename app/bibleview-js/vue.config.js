// vue.config.js
const isProduction = process.env.NODE_ENV !== "production";

let config = {
  lintOnSave: false,
  runtimeCompiler: true,
  transpileDependencies: ["dom-highlight-range"],
  publicPath: "",
  pluginOptions: {
    webpackBundleAnalyzer: {
      openAnalyzer: false
    }
  },
}

if(isProduction) {
  config.productionSourceMap = true;
} else {
  config = {
    ...config,
    configureWebpack: {
      devtool: 'inline-source-map'
    }
  }
}

module.exports = config;
