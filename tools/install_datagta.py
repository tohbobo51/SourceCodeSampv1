#!/usr/bin/env python3
"""Validate DataGta.zip and install its game assets into a selected game-data folder."""
from __future__ import annotations
import argparse
import datetime as dt
import hashlib
import os
from pathlib import Path, PurePosixPath
import shutil
import stat
import sys
import zipfile

CHUNK = 1024 * 1024
EXPECTED_SIZE = 438650686
EXPECTED_SHA256 = "990f9e382594301f1f56e89920792636dc09756884fdf832a02a41a92348b9e6"
GAME_ROOTS = {"anim", "audio", "data", "fonts", "models", "SAMP", "TEXT", "texdb"}
GAME_ROOT_FILES = {"CINFO.BIN", "stream.ini"}
USER_FILES = {
    "settings.ini", "settings.json", "server.ini", "favorites.json",
    "gtasatelem.set", "gta_sa.set", "gtasamp10.b", "samp_log.txt",
    "svlog.txt", "samplog.txt", "sampvoice.txt",
}


def safe_relative(name: str) -> Path | None:
    """Return a safe path relative to the game folder, rejecting traversal."""
    p = PurePosixPath(name)
    if p.is_absolute() or "\\" in name or (p.parts and ":" in p.parts[0]) or any(part in ("..", "") for part in p.parts):
        raise ValueError(f"Path tidak aman dalam ZIP: {name!r}")
    if not p.parts:
        return None
    return Path(*p.parts)


def is_game_asset(rel: Path) -> bool:
    root = rel.parts[0]
    return root in GAME_ROOTS or (len(rel.parts) == 1 and root in GAME_ROOT_FILES)


def is_user_owned(rel: Path) -> bool:
    name = rel.name.lower()
    return name.endswith(".log") or name in USER_FILES


def main() -> int:
    ap = argparse.ArgumentParser(description="Pasang aset game dari DataGta.zip ke folder data game.")
    ap.add_argument("zip_file", help="Lokasi DataGta.zip")
    ap.add_argument("game_dir", help="Folder data game tujuan; untuk aplikasi ini: Android/data/com.xyron.game/files")
    ap.add_argument("--expected-size", type=int, default=EXPECTED_SIZE, help="Ukuran ZIP yang diharapkan dalam byte")
    ap.add_argument("--sha256", default=EXPECTED_SHA256, help="SHA-256 paket DataGta yang diharapkan")
    ap.add_argument("--yes", action="store_true", help="Lewati konfirmasi sebelum pemasangan")
    ap.add_argument("--dry-run", action="store_true", help="Validasi ZIP dan tampilkan rencana tanpa menulis file")
    args = ap.parse_args()

    archive = Path(args.zip_file).expanduser().resolve()
    target = Path(args.game_dir).expanduser().resolve()
    if not archive.is_file():
        ap.error(f"Arsip tidak ditemukan: {archive}")
    if target == Path(target.anchor) or target == archive:
        ap.error("Folder tujuan tidak aman.")
    if archive.stat().st_size != args.expected_size:
        ap.error(f"Ukuran arsip tidak cocok: {archive.stat().st_size} byte; diharapkan {args.expected_size} byte.")
    expected_sha = args.sha256.strip().lower()
    if len(expected_sha) != 64:
        ap.error("Nilai --sha256 harus berisi 64 digit heksadesimal.")
    digest = hashlib.sha256()
    with archive.open("rb") as src:
        while True:
            chunk = src.read(CHUNK)
            if not chunk:
                break
            digest.update(chunk)
    actual_sha = digest.hexdigest()
    if actual_sha != expected_sha:
        ap.error(f"SHA-256 tidak cocok: {actual_sha}")
    print(f"SHA-256 cocok: {actual_sha}")

    try:
        zf = zipfile.ZipFile(archive, "r")
    except (OSError, zipfile.BadZipFile) as exc:
        ap.error(f"Tidak dapat membuka ZIP: {exc}")

    with zf:
        print("Memeriksa integritas arsip (CRC)...")
        bad = zf.testzip()
        if bad:
            ap.error(f"CRC gagal untuk anggota ZIP: {bad}")

        members = []
        excluded = 0
        total_bytes = 0
        seen = set()
        for info in zf.infolist():
            rel = safe_relative(info.filename)
            if rel is None:
                continue
            mode = info.external_attr >> 16
            if stat.S_ISLNK(mode):
                ap.error(f"ZIP berisi symlink yang tidak diizinkan: {info.filename}")
            if info.is_dir():
                continue
            key = rel.as_posix()
            if key in seen:
                ap.error(f"Path duplikat dalam ZIP: {key}")
            seen.add(key)
            if not is_game_asset(rel) or is_user_owned(rel):
                excluded += 1
                continue
            members.append((info, rel))
            total_bytes += info.file_size

        if not members:
            ap.error("Tidak ada aset game yang cocok untuk dipasang.")

        collisions = sum((target / rel).exists() for _, rel in members)
        print(f"Arsip: {archive}\nTujuan: {target}\nFile game: {len(members)}\nFile non-game/log/pengaturan dilewati: {excluded}")
        print(f"Ukuran ekstrak: {total_bytes:,} byte\nFile lama yang dicadangkan: {collisions}")
        if args.dry_run:
            print("Mode dry-run: tidak ada file yang diubah.")
            return 0
        if not args.yes and input("Lanjutkan? File lama akan dicadangkan. [y/N] ").strip().lower() not in ("y", "ya", "yes"):
            print("Dibatalkan; tidak ada perubahan.")
            return 0

        target.mkdir(parents=True, exist_ok=True)
        backup = target / f".datagta-backup-{dt.datetime.now().strftime('%Y%m%d-%H%M%S-%f')}"
        backed_up = installed = 0
        try:
            for index, (info, rel) in enumerate(members, 1):
                dest = target / rel
                dest.parent.mkdir(parents=True, exist_ok=True)
                if not dest.resolve().is_relative_to(target):
                    raise ValueError(f"Path tujuan keluar dari folder game: {rel}")
                if dest.exists():
                    if not dest.is_file():
                        raise IsADirectoryError(f"Tujuan file adalah direktori: {dest}")
                    saved = backup / rel
                    saved.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(dest, saved)
                    backed_up += 1

                tmp = dest.with_name(dest.name + ".datagta-tmp")
                try:
                    with zf.open(info, "r") as src, open(tmp, "wb") as out:
                        shutil.copyfileobj(src, out, CHUNK)
                    os.replace(tmp, dest)
                finally:
                    if tmp.exists():
                        tmp.unlink()
                installed += 1
                if index % 25 == 0 or index == len(members):
                    print(f"[{index}/{len(members)}] terpasang", flush=True)
        except Exception:
            print(f"Pemasangan terhenti; cadangan file lama (jika ada): {backup}", file=sys.stderr)
            raise

        if backed_up == 0 and backup.exists():
            shutil.rmtree(backup)
        print(f"Selesai: {installed} file game dipasang; {excluded} file non-game/log/pengaturan dilewati.")
        if backed_up:
            print(f"Cadangan file yang ditimpa: {backup}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"Gagal: {exc}", file=sys.stderr)
        raise SystemExit(1)
