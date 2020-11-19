// vue.config.js
module.exports = {
  lintOnSave: false,
  runtimeCompiler: true,
  //productionSourceMap: false,
  publicPath: "",
  configureWebpack: {
    devtool: 'inline-source-map'
  }
}
