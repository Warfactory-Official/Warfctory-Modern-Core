#!/usr/bin/env python3
"""Visualise a WFCore radar scan exported by ``/wfcore_radar scan``.

Opens an interactive 2D (X, Z) map of a scan: drag to pan, use the toolbar or scroll wheel to zoom,
and hover any datapoint/cluster centre for its details. Uses only matplotlib + numpy.

Usage::

    python radar_visualizer.py scan-minecraft_overworld-12345.json   # open interactive window
    python radar_visualizer.py scan.json --out map.png               # also save a PNG
    python radar_visualizer.py scan.json --no-window                 # just save, no window
    python radar_visualizer.py scan.json --hide-noise                # drop DBSCAN noise points

Minecraft's +Z axis points south, so the Z axis is inverted to put north at the top, matching the
orientation of the in-game map.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

import numpy as np

import matplotlib
import matplotlib.pyplot as plt
from matplotlib.patches import Rectangle

# GUI backends to try, best-first, when the auto-selected one is non-interactive (headless import).
INTERACTIVE_BACKENDS = ("QtAgg", "Qt5Agg", "TkAgg", "GTK4Agg", "GTK3Agg", "WXAgg", "MacOSX")


def load_scan(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)
    if "points" not in data or "clusters" not in data:
        raise ValueError(f"{path} does not look like a radar scan export (missing points/clusters)")
    return data


def cluster_colors(n: int):
    """A distinct colour per cluster: the qualitative tab20 for small counts, HSV otherwise."""
    if n == 0:
        return []
    cmap = plt.get_cmap("tab20" if n <= 20 else "hsv")
    if n <= 20:
        return [cmap(i % 20) for i in range(n)]
    return [cmap(i / n) for i in range(n)]


def xs_zs(coords):
    """Split a list of ``{"x":.., "z":..}`` dicts into parallel numpy arrays (empty-safe)."""
    if not coords:
        return np.array([]), np.array([])
    xs = np.array([c["x"] for c in coords], dtype=float)
    zs = np.array([c["z"] for c in coords], dtype=float)
    return xs, zs


def plot_scan(data: dict, ax) -> list:
    """Draw the scan onto ``ax``; return a hover registry of ``(scatter_artist, [label, ...])`` pairs."""
    points = data.get("points", [])
    clusters = data.get("clusters", [])
    colors = cluster_colors(len(clusters))
    registry = []

    # --- noise: targets DBSCAN left out of every cluster ---
    noise_struct = [p for p in points if not p.get("clustered") and p.get("type") != "PLAYER"]
    noise_players = [p for p in points if not p.get("clustered") and p.get("type") == "PLAYER"]
    nx, nz = xs_zs(noise_struct)
    if nx.size:
        artist = ax.scatter(nx, nz, s=8, c="#b0b0b0", marker=".", label="Unclustered (noise)", zorder=1)
        registry.append((artist, [
            f"Structure ({p['x']}, {p['z']})\nvalue={p.get('value', 0)}  ·  unclustered" for p in noise_struct
        ]))

    # --- players get gathered up and drawn last so they sit on top; keep parallel hover labels ---
    players_xy = []
    player_labels = []
    for p in noise_players:  # noise players still matter — show them with the rest
        players_xy.append((p["x"], p["z"]))
        player_labels.append(f"Player ({p['x']}, {p['z']})\nunclustered")

    show_cluster_legend = len(clusters) <= 12
    for cluster, color in zip(clusters, colors):
        idx = cluster.get("index", 0)
        struct = [p for p in cluster.get("points", []) if p.get("type") != "PLAYER"]
        sx, sz = xs_zs(struct)
        label = None
        if show_cluster_legend:
            label = (
                f"Cluster #{idx} "
                f"(v={cluster.get('clusterValue', 0)}, p={cluster.get('playerPopulation', 0)})"
            )
        if sx.size:
            artist = ax.scatter(sx, sz, s=14, color=color, marker="o", edgecolors="none",
                                label=label, zorder=2)
            registry.append((artist, [
                f"Structure ({p['x']}, {p['z']})\nvalue={p.get('value', 0)}  ·  cluster #{idx}" for p in struct
            ]))
        elif label is not None:
            # keep a legend entry even for a player-only cluster
            ax.scatter([], [], s=14, color=color, marker="o", label=label)

        # bounding box
        bounds = cluster.get("bounds", {})
        bmin, bmax = bounds.get("min"), bounds.get("max")
        if bmin and bmax:
            x0, x1 = bmin["x"], bmax["x"]
            z0, z1 = bmin["z"], bmax["z"]
            rect = Rectangle(
                (x0, z0),
                (x1 - x0) or 1,
                (z1 - z0) or 1,
                fill=True,
                facecolor=color,
                alpha=0.08,
                edgecolor=color,
                linewidth=1.6,
                linestyle="--",
                zorder=2,
            )
            ax.add_patch(rect)

        # centre marker + annotation (also hoverable, with the cluster summary)
        center = cluster.get("center")
        if center:
            cmarker = ax.scatter(
                center["x"], center["z"], s=130, color=color, marker="X",
                edgecolors="black", linewidths=0.8, zorder=4,
            )
            registry.append((cmarker, [
                f"Cluster #{idx} centre ({center['x']}, {center['z']})\n"
                f"richness={cluster.get('clusterValue', 0)}  ·  players={cluster.get('playerPopulation', 0)}\n"
                f"points={cluster.get('pointCount', len(cluster.get('points', [])))}"
            ]))
            ax.annotate(
                f"#{idx}",
                (center["x"], center["z"]),
                textcoords="offset points",
                xytext=(6, 6),
                fontsize=8,
                fontweight="bold",
                color="black",
            )

        for coord in cluster.get("players", []):
            players_xy.append((coord["x"], coord["z"]))
            player_labels.append(f"Player ({coord['x']}, {coord['z']})\ncluster #{idx}")

    # --- players last, so they sit on top of everything ---
    if players_xy:
        px = [c[0] for c in players_xy]
        pz = [c[1] for c in players_xy]
        artist = ax.scatter(px, pz, s=90, c="red", marker="*", edgecolors="black",
                            linewidths=0.6, label="Player", zorder=5)
        registry.append((artist, player_labels))

    # proxy legend entry for the centre marker (its real scatter carries no label)
    ax.scatter([], [], s=130, color="#444444", marker="X", edgecolors="black", label="Cluster centre")

    ax.set_aspect("equal", adjustable="datalim")
    ax.grid(True, linestyle=":", alpha=0.4)
    ax.set_xlabel("X (west ← → east)")
    ax.set_ylabel("Z (north ↑, south ↓)")
    ax.invert_yaxis()  # north up
    ax.margins(0.05)

    dim = data.get("dimension", "?")
    ax.set_title(
        f"Radar scan — {dim}\n"
        f"{data.get('targetCount', len(points))} targets, {len(clusters)} clusters "
        f"(eps={data.get('eps', '?')}, minPts={data.get('minPts', '?')})"
    )
    ax.legend(loc="upper left", bbox_to_anchor=(1.01, 1.0), fontsize=8, framealpha=0.9)
    return registry


class Interactivity:
    """Adds hover tooltips and scroll-to-zoom to a plotted axes (pan/box-zoom come from the toolbar)."""

    def __init__(self, fig, ax, registry):
        self.fig = fig
        self.ax = ax
        self.registry = registry
        self.annot = ax.annotate(
            "", xy=(0, 0), xytext=(14, 14), textcoords="offset points",
            bbox=dict(boxstyle="round", fc="#ffffe0", ec="0.4", alpha=0.96),
            fontsize=8, zorder=10, annotation_clip=False,
        )
        self.annot.set_visible(False)

    def connect(self):
        self.fig.canvas.mpl_connect("motion_notify_event", self._on_move)
        self.fig.canvas.mpl_connect("scroll_event", self._on_scroll)

    def _on_move(self, event):
        if event.inaxes is not self.ax:
            self._hide()
            return
        for artist, labels in self.registry:
            contains, info = artist.contains(event)
            if not contains:
                continue
            idx = info["ind"][0]
            offsets = artist.get_offsets()
            if idx >= len(offsets):
                continue
            self.annot.xy = (offsets[idx][0], offsets[idx][1])
            self.annot.set_text(labels[idx] if idx < len(labels) else "")
            self.annot.set_visible(True)
            self.fig.canvas.draw_idle()
            return
        self._hide()

    def _hide(self):
        if self.annot.get_visible():
            self.annot.set_visible(False)
            self.fig.canvas.draw_idle()

    def _on_scroll(self, event):
        if event.inaxes is not self.ax or event.xdata is None:
            return
        scale = 1 / 1.2 if event.button == "up" else 1.2
        xlim = self.ax.get_xlim()
        ylim = self.ax.get_ylim()
        self.ax.set_xlim([event.xdata + (edge - event.xdata) * scale for edge in xlim])
        self.ax.set_ylim([event.ydata + (edge - event.ydata) * scale for edge in ylim])
        self.fig.canvas.draw_idle()


def _has_display() -> bool:
    """Best-effort check for a usable GUI, so headless runs fall back to saving cleanly."""
    if sys.platform.startswith("linux"):
        return bool(os.environ.get("DISPLAY") or os.environ.get("WAYLAND_DISPLAY"))
    return True  # macOS / Windows generally have a display


def _ensure_interactive_backend() -> bool:
    """Switch to an interactive backend if the current one is headless. Return True if one is active."""
    if matplotlib.get_backend().lower() not in ("agg", "template", "pdf", "svg", "ps"):
        return True
    for name in INTERACTIVE_BACKENDS:
        try:
            plt.switch_backend(name)
            return True
        except Exception:
            continue
    return False


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Visualise a WFCore radar scan JSON export.")
    parser.add_argument("scan", type=Path, help="Path to the scan JSON written by /wfcore_radar scan")
    parser.add_argument("--out", type=Path, default=None, help="Also save a PNG to this path")
    parser.add_argument("--no-window", action="store_true", help="Don't open a window; just save an image")
    parser.add_argument("--hide-noise", action="store_true", help="Do not draw unclustered noise points")
    parser.add_argument("--dpi", type=int, default=140, help="Saved-image DPI (default: 140)")
    args = parser.parse_args(argv)

    try:
        data = load_scan(args.scan)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    if args.hide_noise:
        data = dict(data)
        data["points"] = [p for p in data.get("points", []) if p.get("clustered")]

    interactive = False
    if not args.no_window:
        if _has_display() and _ensure_interactive_backend():
            interactive = True
        else:
            print("note: no interactive display available; saving an image instead", file=sys.stderr)

    fig, ax = plt.subplots(figsize=(11, 9))
    registry = plot_scan(data, ax)
    fig.tight_layout()

    # Always produce a file when there's no window, so a headless run still leaves an artifact.
    out = args.out
    if out is None and not interactive:
        out = args.scan.with_suffix(".png")
    if out is not None:
        fig.savefig(out, dpi=args.dpi, bbox_inches="tight")
        print(f"wrote {out}")

    if interactive:
        Interactivity(fig, ax, registry).connect()
        print("opening interactive window — drag to pan, scroll to zoom, hover for details")
        plt.show()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
