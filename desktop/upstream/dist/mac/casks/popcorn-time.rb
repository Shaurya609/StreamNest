cask "popcorn-time" do
  version "0.5.1"

  nwjs = "0.86.0"
  arch = "x64"

  name token.gsub(/\b\w/, &:capitalize)
  desc "BitTorrent client that includes an integrated media player"
  homepage "https://github.com/popcorn-official/popcorn-desktop/releases/download/v0.5.1/Popcorn-Time-0.5.1-osx64.zip"

  repo = "popcorn-official/popcorn-desktop"
  zip = "#{name.first}-#{version}-osx64.zip"

  livecheck { url "https://github.com/#{repo}" }

  if (%w[-v --verbose -d --debug] & ARGV).any?
    v = "-v"
    quiet = silent = "verbose"
  else
    quiet = "quiet"
    silent = "silent"
  end

  sha256 "119225ce4c8f3049358412e7230fba5a05c3af2b7168bf71acdc178d994c2f28"

  url "https://github.com/popcorn-official/popcorn-desktop/releases/download/v0.5.1/Popcorn-Time-0.5.1-osx64.zip"

  auto_updates true
  depends_on arch: :x86_64

  app "#{name.first}.app"

  app_support = "#{Dir.home}/Library/Application Support"

  uninstall quit: bundle_id = "com.nw-builder.#{token}"

  zap trash: %W[
    #{app_support}/#{name.first}
    ~/Library/Preferences/#{bundle_id}.plist
    #{app_support}/com.apple.sharedfilelist/com.apple.LSSharedFileList.ApplicationRecentDocuments/#{bundle_id}.sfl*
    #{app_support}/configstore/#{token}.json
    ~/Library/Saved Application State/#{bundle_id}.savedState
    ~/Library/Caches/#{name.first}
  ]
end
