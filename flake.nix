{
  description = "TennaRC: an Android remote for LG, Samsung, Roku, Android TV and Fire TV";

  inputs.nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };

      buildToolsVersion = "37.0.0";

      android = pkgs.androidenv.composeAndroidPackages {
        platformVersions = [ "37.0" ];
        buildToolsVersions = [ buildToolsVersion ];
        includeNDK = false;
        includeEmulator = false;
        includeSystemImages = false;
        includeSources = false;
      };
      sdkRoot = "${android.androidsdk}/libexec/android-sdk";
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        packages = with pkgs; [
          android-tools
          android.androidsdk
          jdk21
          qrencode
          python3
        ];

        ANDROID_HOME = sdkRoot;
        ANDROID_SDK_ROOT = sdkRoot;
        JAVA_HOME = "${pkgs.jdk21}";
        GRADLE_OPTS = "-Dorg.gradle.project.android.builder.sdkDownload=false";
      };
    };
}
