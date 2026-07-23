{
  description = "Entorno de build para el plugin IntelliJ de Game of Trees (got)";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs =
    { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs { inherit system; };
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        packages = [
          pkgs.jdk21
          pkgs.gradle
        ];

        JAVA_HOME = "${pkgs.jdk21}/lib/openjdk";
      };
    };
}
