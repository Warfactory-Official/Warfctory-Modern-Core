@Library('wf-ci') _

wfBuild(
    repo: 'Warfctory-Modern-Core',
    upstream: ['CombinedAssault/main'],
    jdk: '21',
    branches: ['1.21.1'],
    build: 'build --no-daemon --stacktrace',
    publish: 'publish --no-daemon --stacktrace',
    latest: [
        tag: 'latest-1.21.1',
        title: 'Latest 1.21.1 build (${sha})',
        notes: 'Automated NeoForge 1.21.1 build of ${sha}',
        filesCmd: 'ls build/libs/*.jar',
        latest: false,
    ],
)
