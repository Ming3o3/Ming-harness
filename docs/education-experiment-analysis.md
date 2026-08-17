# 教育检索实验离线分析

系统的管理员教育实验面板提供 `GET /api/education/experiments/samples.csv`。该文件是
逐 Run 的去标识样本：学习者、学习目标和 Run 使用稳定哈希键，状态、语言、检索证据和
形成性测评均来自 Run 创建时冻结的事实。

## 运行方式

```bash
python3 scripts/education_experiment_analysis.py education-experiment-samples.csv \
  --json-out education-experiment-analysis.json \
  --summary-csv education-experiment-summary.csv
```

脚本只使用 Python 标准库，不需要安装额外依赖。输出包括：

- 按实际执行策略聚合的运行数、证据覆盖、测评正确率、掌握度增益和目标达成率；
- 按 `effective_strategy + conditioning` 的学习者状态分层；
- 按 `effective_strategy + programming_language` 的语言分层；
- 同一学习者—目标内以 `FULL` 为参考的配对结果；
- `FULL`、`NO_LEARNER_STATE`、`NO_DEPENDENCY_GRAPH`、`NO_STATE_NO_GRAPH` 四臂联合消融及 interaction。

学习结果的默认分析口径是 `run_status == SUCCEEDED` 且 `assessment_count > 0`。掌握度增益
按形成性测评数量加权；样本状态只表示描述性统计门槛，不执行显著性检验，也不把描述性
证据归因解释为因果效果。论文中的 p 值、置信区间和混合效应模型应在此 JSON/CSV 基础上
由统计软件完成。

生产端和离线端共同依赖样本 CSV 的冻结字段，不会重新读取当前学习者状态，因此历史
Run 的分析不会随着后续测评或当前服务器时间变化而漂移。
