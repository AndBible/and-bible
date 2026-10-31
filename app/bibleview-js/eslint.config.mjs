import js from '@eslint/js'
import vue from 'eslint-plugin-vue'
import globals from 'globals'
import { defineConfigWithVueTs, vueTsConfigs } from '@vue/eslint-config-typescript'

export default defineConfigWithVueTs(
  {
    ignores: [
      '**/.DS_Store', '**/node_modules/**', '**/dist/**', '**/build/**',
      '**/.env.local', '**/.env.*.local', '**/*-debug.log*',
      '**/yarn-error.log*', '**/.idea/**', '**/.vscode/**',
      '**/*.suo', '**/*.ntvs*', '**/*.njsproj', '**/*.sln', '**/*.sw?',
      'ios/webkit/last-run.png',
    ],
  },
  js.configs.recommended,
  ...vue.configs['flat/essential'],
  // Match the old base preset, rather than introducing opinionated TS rules.
  vueTsConfigs.base,
  vueTsConfigs.eslintRecommended,
  { languageOptions: { globals: { ...globals.node, ...globals.browser } } },
  { files: ['**/*.cjs'], languageOptions: { sourceType: 'commonjs' } },
  {
    files: ['**/*.{ts,cts,mts,tsx,vue}'],
    rules: {
      'no-unused-vars': 'off',
      'no-undef': 'off',
      '@typescript-eslint/no-unused-vars': 'warn',
    },
  },
  {
    files: ['src/components/OSIS/*.vue', 'src/components/MyBible/*.vue', 'src/components/HTML/*.vue'],
    rules: { 'vue/multi-word-component-names': 'off' },
  },
)
