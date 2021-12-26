module.exports = {
  preset: '@vue/cli-plugin-unit-jest',
  transform: {
    '^.+\\.vue$': 'vue-jest',
    '^.+\\.xml': 'jest-raw-loader',
    '^.+\\.html': 'jest-raw-loader',
    '^.+\\.txt': 'jest-raw-loader',
  },
}
