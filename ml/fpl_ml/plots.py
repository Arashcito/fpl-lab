"""Shared chart style for the notebooks: light surface, recessive grid and axes,
thin marks, a fixed categorical colour order (validated for colour-blind safety)."""
from __future__ import annotations

import matplotlib.pyplot as plt

SERIES = ["#257de0", "#f47230", "#00b894", "#f2a600"]   # blue, orange, aqua, yellow - always in this order
TEXT = "#191919"
TEXT_SECONDARY = "#666666"
GRID = "#e7e7e7"
AXIS = "#c2c2c2"


def use_style() -> None:
    plt.rcParams.update({
        "figure.figsize": (8, 4.2),
        "figure.dpi": 110,
        "figure.facecolor": "#ffffff",
        "axes.facecolor": "#ffffff",
        "axes.edgecolor": AXIS,
        "axes.linewidth": 0.8,
        "axes.spines.top": False,
        "axes.spines.right": False,
        "axes.grid": True,
        "axes.grid.axis": "y",
        "grid.color": GRID,
        "grid.linewidth": 0.8,
        "axes.titlesize": 12,
        "axes.titleweight": "bold",
        "axes.titlecolor": TEXT,
        "axes.labelcolor": TEXT_SECONDARY,
        "xtick.color": TEXT_SECONDARY,
        "ytick.color": TEXT_SECONDARY,
        "text.color": TEXT,
        "lines.linewidth": 2,
        "legend.frameon": False,
        "axes.prop_cycle": plt.cycler(color=SERIES),
    })


def label_bars(ax, fmt: str = "{:.1f}") -> None:
    """Value labels on bars, in the text colour (never the series colour)."""
    for container in ax.containers:
        ax.bar_label(container, labels=[fmt.format(v) for v in container.datavalues],
                     padding=3, fontsize=9, color=TEXT)
