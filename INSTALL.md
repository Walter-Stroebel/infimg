# Installing infimg

infimg is installed and run through [MITSA](https://github.com/Walter-Stroebel/mitsa),
a small cross-platform launcher/updater. MITSA handles fetching the right
release jar, keeping it updated, and giving you a plain `infimg` command
on `PATH` — no manual jar downloads or per-OS scripts to babysit.

## 1. Install MITSA

Follow MITSA's own [INSTALL.md](https://github.com/Walter-Stroebel/mitsa/blob/main/INSTALL.md)
(requires Java 17, same as infimg — MITSA doesn't bundle a JVM, you need
one on `PATH` either way).

## 2. Register and run infimg

```bash
mitsa add infimg Walter-Stroebel infimg 'infimg-.*-jar-with-dependencies\.jar'
```

This registers infimg, fetches the latest release jar, and writes an
`infimg` command onto your `PATH` (`~/bin/infimg` on Linux/macOS) whose
entire job is to hand off to `mitsa run infimg`. From then on:

```bash
infimg [-0..-9] [optional-image-file]
```

works like any normal installed command. `mitsa update infimg` pulls the
latest release when you want it.

## Developers

Building from source doesn't involve MITSA — see the "Build (development)"
section in [README.md](README.md).
