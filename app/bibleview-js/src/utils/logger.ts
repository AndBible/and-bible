export class Logger {
    module: string;

    constructor({module}: {module: string}){
        this.module = module;
    }

    info(...data: any[]){
        console.log(this.module+':', ...data);
    }
}