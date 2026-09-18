Native core staging directory
=============================

Jib copies the contents of `src/main/jib/` into the image root, so anything placed at
`src/main/jib/opt/hokeka/lib/` lands at `/opt/hokeka/lib/` in the container.

CI drops the Linux build of the Rust native core here before running Jib:

    cd edge-engine
    rustup target add x86_64-unknown-linux-gnu
    cargo build -p edge-jni --release --target x86_64-unknown-linux-gnu
    cp target/x86_64-unknown-linux-gnu/release/libedge_engine.so \
       ../edge-host/src/main/jib/opt/hokeka/lib/

The container starts the JVM with `-Djava.library.path=/opt/hokeka/lib` (set in the pom's Jib
config), so `System.loadLibrary("edge_engine")` finds it. If the file is absent, the host runs on
the Java interpreter fallback — the image is still fully functional, just not native-accelerated.
