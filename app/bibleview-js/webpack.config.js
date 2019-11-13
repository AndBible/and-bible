let webpack = require('webpack');
let path = require('path');

let autoprefixer = require('autoprefixer');
let precss = require('precss');
let {VueLoaderPlugin} = require('vue-loader');

const MiniCssExtractPlugin = require("mini-css-extract-plugin");
const Fiber = require("fibers");

const extractSass = new MiniCssExtractPlugin({
    filename: "[name].[chunkhash].css",
});


const babelOptions = {
    babelrc: false,
    plugins: [
        ["transform-builtin-classes", {globals: ["Error"]}],
        "transform-class-properties",
        "transform-es2015-classes",
        "transform-async-to-generator",
        "transform-object-rest-spread",
    ],
    presets: [
        ["env", {
            "targets": {
                "browsers": ["Android >= 4"]
            }
        }]
    ]
};

let loaderOptions = {
    minimize: true,
    //debug: true,
    postcss: function () {
        return [autoprefixer({browsers: ['Android >= 4']}), precss];
    }
};

let options = {
    // Application 'main' file, relative to context (context defaults to process.cwd())
    mode: 'production',
    entry: {
        loader: ['babel-polyfill', './src/index.js']
    },
    devtool: false,
    stats: 'normal',
    context: path.resolve('./'),
    output: {
        publicPath: '',
        path: path.resolve('dist'),
        filename: '[name].js',
        chunkFilename: 'modules/[name]/[chunkhash].js',
    },
    optimization: {
        minimize: true
    },
    // Configure module loading
    module: {
        rules: [
            {
                test: /\.vue$/,
                exclude: /node_modules/,
                use: [{loader: 'vue-loader'}, {loader: 'eslint-loader'}]
            },
            {
                test: /\.js$/,
                // NOTE: Webpack tries to resolve its loaders as modules. If resolution fails, it
                // may complain about missing modules _while_ going through one of your sources.
                // You may want to double check that the webpack build's dependencies are installed
                // before assuming there's anything wrong with the project sources.
                loader: 'babel-loader?cacheDirectory',
                exclude: file => {
                    return (
                        /node_modules/.test(file) &&
                        !/\.vue\.js/.test(file)
                    );
                },
                options: babelOptions,
            },
            {
                // override bundle loader module so it can be found inside linked modules
                test: /bundle!/,
                loader: require.resolve('bundle-loader')
            },
            {
                test: /\.html$/,
                exclude: /[\/\\]node_modules[\/\\]/,
                loaders: ['html-loader']
            },
            {
                test: /\.s?[ac]ss$/,
                use: [
                    MiniCssExtractPlugin.loader,
                    {
                        loader: 'css-loader?minimize',
                        options: {
                            sourceMap: true
                        }
                    },
                    {
                        loader: 'sass-loader',
                        options: {
                            implementation: require("sass"),
                            fiber: Fiber,
                            sourceMap: true
                        }
                    },
                ],
            },
            {
                test: /\.eot?(\?v=\d+\.\d+\.\d+)?$/,
                loader: 'url-loader?name=fonts/[hash].[ext]&limit=10000?mimetype=application/vnd.ms-fontobject'
            },
            {
                test: /[\/\\](webfonts|fonts)[\/\\].+?\.svg?(\?v=\d+\.\d+\.\d+)?$/,
                loader: 'url-loader?name=fonts/[hash].[ext]&limit=100?mimetype=image/svg+xml'
            },
            {
                test: /\.ttf?(\?v=\d+\.\d+\.\d+)?$/,
                loader: 'url-loader?name=fonts/[hash].[ext]&limit=100?mimetype=application/octet-stream'
            },
            {
                test: /\.woff2?(\?v=\d+\.\d+\.\d+)?$/,
                loader: 'url-loader?name=fonts/[hash].[ext]&limit=100?mimetype=application/font-woff'
            },
            {
                test: /.(png|jpg|gif)$/,
                loader: 'url-loader?name=images/[hash].[ext]&limit=200?mimetype=image/[ext]'
            },
            {
                test: /\/(images|icons)\/.*\.svg$/,
                loader: 'url-loader?name=images/[hash].[ext]&limit=200?mimetype=image/svg+xml'
            },
            {
                test: /[\/\\]images[\/\\].+?\.(?:json|geojson)$/,
                loader: 'url-loader?name=images/[hash].[ext]&limit=200?mimetype=application/json'
            },
        ]
    },
    // This configures module resolution in our project sources.
    resolve: {
        // Webpack tries these extensions with any name passed to require/import.
        extensions: ['.js', '.vue'],

        modules: ['.', path.join(process.cwd(), 'node_modules'), 'node_modules'],
        // Aliases for names used with import/require.
        alias: {
//            'ui-select-css': path.resolve('./node_modules/ui-select/dist/select.css'),
        }
    },
    resolveLoader: {
        modules: [path.join(process.cwd(), 'node_modules')]
    },
    plugins: [
        new VueLoaderPlugin(),
        new webpack.LoaderOptionsPlugin(loaderOptions),
        new webpack.DefinePlugin({
            VERSION: JSON.stringify(require("./package.json").version),
        }),
        extractSass,
        new webpack.SourceMapDevToolPlugin({
            filename: '[file].map',
            publicPath: 'http://localhost:63341/bibleview-js/dist/'
            //publicPath: 'file://android_asset/web/'
        }),
        new webpack.ProvidePlugin({
            $: "jquery",
            jQuery: "jquery"
        })
    ],
};

module.exports = options;
