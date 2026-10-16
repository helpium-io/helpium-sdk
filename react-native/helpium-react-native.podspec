require 'json'

package = JSON.parse(File.read(File.join(__dir__, 'package.json')))

Pod::Spec.new do |s|
  s.name         = 'helpium-react-native'
  s.version      = package['version']
  s.summary      = package['description']
  s.license      = package['license']
  s.authors      = package['author']
  s.homepage     = package['homepage']
  s.platforms    = { :ios => '15.0' }
  s.source       = { :git => 'https://github.com/helpium-io/helpium-sdk.git', :tag => "v#{s.version}" }
  s.swift_version = '5.9'

  # The bridge plus the Helpium iOS SDK sources (copied by scripts/sync-native.mjs).
  s.source_files = 'ios/*.{h,m,mm,swift}', 'ios/vendor/Helpium/**/*.swift'
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES' }

  if respond_to?(:install_modules_dependencies, true)
    install_modules_dependencies(s)
  else
    s.dependency 'React-Core'
  end
end
