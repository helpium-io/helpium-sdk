// swift-tools-version:5.9
// Swift Package Manager reads the package from the repository root.
import PackageDescription

let package = Package(
    name: "Helpium",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "Helpium", targets: ["Helpium"]),
    ],
    targets: [
        .target(name: "Helpium", path: "ios/Sources/Helpium"),
        .testTarget(
            name: "HelpiumTests",
            dependencies: ["Helpium"],
            path: "ios/Tests/HelpiumTests",
            resources: [.copy("Fixtures")]
        ),
    ]
)
