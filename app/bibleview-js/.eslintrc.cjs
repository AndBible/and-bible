/* eslint-env node */
require('@rushstack/eslint-patch/modern-module-resolution')

module.exports = {
  root: true,
  'extends': [
    'plugin:vue/vue3-essential',
    'eslint:recommended',
    '@vue/eslint-config-typescript'
  ],
  parserOptions: {
    ecmaVersion: 'latest'
  },
  env: {
    node: true
  },
  overrides: [
    {
      files: ["src/components/OSIS/*.vue", "src/components/MyBible/*.vue"],
      rules: {
        "vue/multi-word-component-names": "off"
      }
    }

  ]
}
