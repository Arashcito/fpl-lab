"""The models that are compared.

- last5_average:  baseline - a player's average points over his previous 5 matches
- fpl_official:   FPL's own expected points (the `xP` column of the dataset)
- ridge:          linear regression with L2 regularisation
- lightgbm:       gradient-boosted decision trees
"""
from __future__ import annotations

import lightgbm as lgb
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.impute import SimpleImputer
from sklearn.linear_model import Ridge
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

from .features import CATEGORICAL_FEATURES, FEATURES, NUMERIC_FEATURES


def last5_average(df: pd.DataFrame, fallback: pd.Series) -> pd.Series:
    """Previous-5-match average points; position average when there is no history yet."""
    return df["total_points_last5"].fillna(df["position"].astype(str).map(fallback)).fillna(0)


def make_ridge(alpha: float = 10.0) -> Pipeline:
    pre = ColumnTransformer([
        ("num", Pipeline([
            ("impute", SimpleImputer(strategy="median", add_indicator=True)),
            ("scale", StandardScaler()),
        ]), NUMERIC_FEATURES),
        ("cat", OneHotEncoder(handle_unknown="ignore"), CATEGORICAL_FEATURES),
    ])
    return Pipeline([("pre", pre), ("model", Ridge(alpha=alpha))])


def make_lightgbm(**params) -> lgb.LGBMRegressor:
    defaults = dict(
        n_estimators=600,
        learning_rate=0.03,
        num_leaves=31,
        min_child_samples=100,
        subsample=0.8,
        subsample_freq=1,
        colsample_bytree=0.8,
        reg_lambda=1.0,
        random_state=42,
        verbose=-1,
    )
    defaults.update(params)
    return lgb.LGBMRegressor(**defaults)


def fit(model, train: pd.DataFrame):
    model.fit(train[FEATURES], train["total_points"])
    return model


def predict(model, df: pd.DataFrame) -> pd.Series:
    # predicted points can't be negative on average; clip the rare tiny negatives
    return pd.Series(model.predict(df[FEATURES]), index=df.index).clip(lower=0)
