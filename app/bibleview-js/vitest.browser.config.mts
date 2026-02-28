import {config} from "./vite.config.mts";
import { defineConfig as defineVitestConfig } from 'vitest/config';

const browserTestConfig: any = {
    ...config,
    test: {
        include: ["src/__tests__/**/*.browser.spec.ts"],
        browser: {
            enabled: true,
            provider: "playwright",
            instances: [
                {browser: "chromium"},
            ],
        },
    }
}

export default defineVitestConfig(browserTestConfig)
