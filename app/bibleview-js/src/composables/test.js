const fs = require('fs')

fs.readFile('./refparser.js', 'utf8', (err, data) => {
    /*const dataUri = 'data:text/javascript;charset=utf-8,' + encodeURIComponent(data)
    import(dataUri).then((module) => {
        console.log(module)
    })*/
    var module = {}
    Function(data).call(module)
    console.log(new module.bcv_parser)
})

/*import('./refparser.js').then(module => {
    console.log(module)
})*/
