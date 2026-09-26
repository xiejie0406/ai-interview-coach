module.exports = {
  appId: 'com.aden.desktop',
  productName: 'Aden',
  directories: { output: 'dist/release' },
  files: ['out/**/*', 'package.json'],
  asar: true,
  win: { target: ['nsis'] },
  nsis: {
    oneClick: false,
    perMachine: false,
    allowToChangeInstallationDirectory: false,
    artifactName: 'Aden-${version}-Setup.${ext}'
  }
}
