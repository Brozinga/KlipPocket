# Cross-compiling the Pillow 9.5.0 Android wheels

Moonraker requires `pillow >= 9.5.0`. Chaquopy's wheel mirror
(`https://chaquo.com/pypi-13.1/pillow/`) only ships **9.2.0** as a `cp310` wheel;
everything newer there is `cp312`-only. With `python { version = "3.10" }`, pip
falls back to a source build that fails (`RequiredDependencyException: jpeg` — it
can't find Chaquopy's bundled libjpeg).

So the three wheels in `app/python_wheels/` are **cross-compiled from source**:

| File | ABI |
|------|-----|
| `Pillow-9.5.0-0-cp310-cp310-android_21_arm64_v8a.whl` | arm64-v8a |
| `Pillow-9.5.0-0-cp310-cp310-android_21_armeabi_v7a.whl` | armeabi-v7a |
| `Pillow-9.5.0-0-cp310-cp310-android_21_x86_64.whl` | x86_64 |

They only include **JPEG + zlib** support (Moonraker's `file_manager/metadata.py`
only calls `Image.open()` + `.thumbnail()`); no freetype/tiff/webp/lcms/openjpeg.

## Ingredients (all downloadable, no Chaquopy build server needed)

1. **Target Python** headers + `libpython3.10.so`:
   `com.chaquo.python:target:3.10.13-0` per ABI. Either from
   `~/.gradle/caches/.../com.chaquo.python/target/3.10.13-0/*/target-3.10.13-0-<abi>.zip`
   after any local build, or from Maven Central:
   `https://repo1.maven.org/maven2/com/chaquo/python/target/3.10.13-0/target-3.10.13-0-<abi>.zip`
2. **libjpeg** headers + `.so`:
   `https://chaquo.com/pypi-13.1/chaquopy-libjpeg/chaquopy_libjpeg-1.5.3-1-py3-none-android_<api>_<abi>.whl`
   (a plain zip). The runtime `libjpeg_chaquopy.so` is pulled automatically by
   Chaquopy at APK-build time because Pillow's `.so` records it as `DT_NEEDED`.
3. **zlib**: don't vendor it — the NDK sysroot ships `zlib.h` + `libz.so`.
4. **NDK 23.2.8568313** and a **host Python 3.10** (`pyenv 3.10.14`) as the
   `setup.py` driver.
5. Pillow **9.5.0 sdist** from PyPI.

## Build

The script that produced these wheels is in the scratch area used for this work;
its shape (per ABI):

```sh
# stage a prefix/ with:
#   include/python3.10/*.h        (from target zip)
#   include/{jconfig,jerror,jmorecfg,jpeglib}.h   (from libjpeg wheel)
#   lib/libpython3.10.so          (from target zip)
#   lib/libjpeg_chaquopy.so       (from libjpeg wheel)
#   lib/libjpeg.so -> libjpeg_chaquopy.so   (symlink; setup.py looks for libjpeg.so,
#                                            but the real SONAME is libjpeg_chaquopy.so
#                                            so DT_NEEDED comes out correct)

export CC="$NDK/.../bin/<triple><api>-clang"     # e.g. armv7a-linux-androideabi21-clang
export CXX="${CC}++"
export CFLAGS="-I$PREFIX/include -I$PREFIX/include/python3.10 -fPIC"
export LDFLAGS="-L$PREFIX/lib -Wl,--no-undefined -lm -lpython3.10"
export LDSHARED="$CC -shared"
export JPEG_ROOT="$PREFIX"
export _PYTHON_HOST_PLATFORM="linux-armv7l"      # or linux-x86_64
export PKG_CONFIG=/nonexistent-pkg-config        # CRITICAL: stop setup.py finding host libs
export CPATH="$NDK_SYSROOT/usr/include"
export LIBRARY_PATH="$NDK_SYSROOT/usr/lib/<triple>/<api>"

<host-python3.10> setup.py build_ext --disable-platform-guessing
```

Then package as a wheel:
- bare `.so` names (strip the `.cpython-310-...` SOABI suffix — Chaquopy's own
  historical wheels have no suffix), `llvm-strip --strip-unneeded`;
- pure `.py` copied straight from the sdist's `src/PIL/`;
- a real `RECORD` (`path,sha256=<urlsafe-b64-nopad>,<size>` — Chaquopy parses the
  size as an int and **fails the build** if it's empty);
- `WHEEL` tag `cp310-cp310-android_21_<abi>`;
- filename `Pillow-9.5.0-0-cp310-cp310-android_21_<abi>.whl`, dropped into
  `app/python_wheels/` (already on pip `--find-links`).

## Verify (no device needed)

- `llvm-readelf -h _imaging.so` → right `Class` / `Machine` per ABI.
- `llvm-readelf -d _imaging.so | grep NEEDED` → exactly
  `libm libpython3.10 libjpeg_chaquopy libz libdl libc` — **no host libs**.
  (`-Wl,--no-undefined` makes the link itself fail if a symbol doesn't resolve,
  which is strong evidence the ABI is right.)
- `setup.py` summary prints `--- JPEG support available` /
  `--- ZLIB (PNG/ZIP) support available`.
- After `./gradlew :app:assemble<Flavor>Debug`: extract
  `assets/chaquopy/requirements-common.imy` from the APK (it's a zip after a
  `PK\x03\x04` prefix) and confirm `PIL/_imaging.so` is present and the right
  architecture.

This recipe is Chaquopy's own, reconstructed from their public `chaquo/chaquopy`
repo (`server/pypi/build-wheel.py`, `target/android-env.sh`,
`server/pypi/packages/pillow/`). Re-check that repo first for any future package
or Pillow bump.
