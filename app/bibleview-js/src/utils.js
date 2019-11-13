export class Deferred {
    constructor() {
        this.promise = null;
        this.reset();
    }

    reset() {
        if(this.promise != null) {
            throw new Error("Previous promise is still there!");
        }
        this.promise = new Promise((resolve, reject) => {
            this._resolve = (...args) => {
                resolve(...args);
                this.promise = null;
                this._resolve = () => {};
                this._reject = () => {};
            };
            this._reject = (...args) => {
                reject(...args);
                this.promise = null;
                this._resolve = () => {};
                this._reject = () => {};
            }
        });
        this._waiting = false;
    }

    async wait() {
        if(this._waiting) {
            throw new Error("Multiple waits!");
        }

        if(this.promise) {
            this._waiting = true;
            return await this.promise;
        }
    }

    isWaiting() {
        return this._waiting;
    }

    isRunning() {
        return this.promise !== null;
    }

    resolve(...args) {
        this._resolve(...args);
    }

    reject(...args) {
        this._reject(...args);
    }
}
