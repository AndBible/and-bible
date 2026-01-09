import {inject} from "vue";
import {appSettingsKey} from "@/types/constants";

export const runningInAndroid = !!window.android; // this constant probably belongs somewhere else but only one place

export class Logger {
    module: string;
    androidOnlyIfErrorBoxActive: boolean;

    constructor({module}: {module: string}){
        this.module = module;
        this.androidOnlyIfErrorBoxActive = false;
    }

    androidOnlyIfErrorBox(){
        this.androidOnlyIfErrorBoxActive = true;
        return this;
    }

    vueWarnHandler(msg: any, instance: any, trace: any){
        this.androidOnlyIfErrorBoxActive = true;
        this._log("warn", msg, instance, trace);
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
        let emitThis = true;
        if(runningInAndroid){
            data = data.map(x => typeof(x) == "object" ? JSON.stringify(x) : x);
            const appSettings = inject(appSettingsKey);
            if (!appSettings?.errorBox) {
                emitThis = false;
            }
        }
        this.androidOnlyIfErrorBoxActive = false;
        if(emitThis){
            console[method](this.module+":", ...data);
        }
    }
}