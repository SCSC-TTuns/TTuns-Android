// 루트 빌드 스크립트(필요 최소)
tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
