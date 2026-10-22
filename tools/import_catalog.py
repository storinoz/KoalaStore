"""Extrai apenas nomes, categorias e preços de produtos; abre o banco em modo somente leitura."""
import argparse
import json
import sqlite3
from decimal import Decimal
from pathlib import Path

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("database", nargs="?", type=Path, default=root / "base files" / "vendas.db")
args = parser.parse_args()
with sqlite3.connect(args.database.resolve().as_uri() + "?mode=ro", uri=True) as db:
    db.execute("PRAGMA query_only=ON")
    rows = db.execute("SELECT p._id,p.nome,p.valorvenda,c.nome FROM produto p LEFT JOIN categoria c ON c._id=p.id_categoria ORDER BY p.nome").fetchall()
products = []
for source_id, name, price, category in rows:
    name = " ".join((name or "").split())
    category = " ".join((category or "Sem categoria").split())
    amount = Decimal(str(price or 0)) * 100
    if not name or amount != amount.to_integral_value() or not 0 <= amount <= 100_000_000:
        raise ValueError("Há um produto sem nome ou com preço inválido; a importação foi interrompida.")
    products.append(dict(sourceId=source_id, name=name, category=category, priceCents=int(amount)))
destination = root / "base files" / "legacy_catalog-local.json"
destination.parent.mkdir(parents=True, exist_ok=True)
destination.write_text(json.dumps(dict(catalogId="legacy-vendas-products-v1", products=products), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Catálogo preparado: {len(products)} produtos. O banco original foi preservado.")
