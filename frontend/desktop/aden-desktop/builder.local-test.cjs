module.exports = {
  appId: 'com.aden.desktop.localtest',
  productName: 'Aden Local Test',
  directories: { output: 'dist/local-test' },
  files: ['out/**/*', 'package.json', 'local-test.json'],
  asar: false,
  win: { target: ['nsis'] },
  nsis: {
    oneClick: false,
    perMachine: false,
    allowToChangeInstallationDirectory: false,
    createDesktopShortcut: true,
    artifactName: 'Aden-Local-Test-${version}-Setup.${ext}'
  }
}
