# 学习计划模板源数据

本目录是学习计划模板的人工维护入口。`data/learning-plan-template-seed/` 是后端导入使用的聚合产物，不直接手工编辑。

## 目录规则

- `template_order.json`：声明需要进入聚合 seed 的模板顺序。
- `templates/<templateId>/template.json`：单个模板的主体信息和阶段规划。
- `templates/<templateId>/problem_refs.jsonl`：单个模板的题目引用明细。

`template.json` 不维护 `difficultyMix`、本地匹配数、缺失题列表和 `sourceTags` 这类派生统计；`problem_refs.jsonl` 不维护 `metadata.matchedLocalProblem`。这些字段由生成器根据当前 `data/seed/problems.jsonl` 重新计算。

## 生成命令

```bash
python3 tools/learning_plan_template_seed/prepare_template_seed.py
```

生成结果固定写入 `data/learning-plan-template-seed/` 下的四个文件：

- `learning_plan_templates.jsonl`
- `learning_plan_template_problem_refs.jsonl`
- `learning_plan_template_seed_manifest.json`
- `learning_plan_template_seed_metadata.md`
