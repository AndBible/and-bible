import inspect from "browser-util-inspect";
import {inject, hasInjectionContext} from "vue";
import {appSettingsKey} from "@/types/constants";

export const runningInAndroid = !!window.android; // this constant probably belongs somewhere else but only one place

const documentWarningStatistics: Record<string, number> = {};

export class Logger {
    module: string;
    documentWarningActive: boolean;

    constructor({module}: {module: string}){
        this.module = module;
        this.documentWarningActive = false;
    }

    documentWarning(){
        this.documentWarningActive = true;
        return this;
    }

    vueWarnHandler(msg: string, instance: any, trace: any){
        if(msg.startsWith("Missing required prop: ")){
            this.documentWarningActive = true;
        }
        this._log("warn", {msg, instance, trace});
    }

    warn(...data: any[]){
        this._log("warn", ...data);
    }

    info(...data: any[]){
        this._log("log", ...data);
    }

    debug(...data: any[]){
        this._log("debug", ...data);
    }

    _log(method: "warn"|"log"|"debug", ...data: any[]){
        const msg = data[0];
        data = [this.module+":", ...data];
        if(this.documentWarningActive){
            if(!documentWarningStatistics[msg]){
                documentWarningStatistics[msg] = 0;
            }
            data = [`document warning ${documentWarningStatistics[msg]}:`, ...data];
            documentWarningStatistics[msg]++;
        }
        let emitThis = true;
        if(runningInAndroid){
            data = data.map(x => typeof(x) == "object" ? inspect(x) : x);
            if(hasInjectionContext()){
            const appSettings = inject(appSettingsKey);
                if (this.documentWarningActive && !appSettings?.errorBox) {
                    emitThis = false;
            }
        }
        }
        if(emitThis){
            console[method](...data);
        }
        this.documentWarningActive = false;
    }
}