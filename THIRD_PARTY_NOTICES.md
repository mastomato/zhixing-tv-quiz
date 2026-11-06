# 题库来源与许可

本项目的 MIT `LICENSE` 仅适用于应用代码、界面图标及本项目生成的声音素材。`app/src/main/assets/questions.json` 与 APK 内的题库由多种来源合并，不能统一按 MIT 使用。

## giszh86/cn-math-taxonomy：3,314 题

- 原始项目：[giszh86/cn-math-taxonomy](https://github.com/giszh86/cn-math-taxonomy)
- 原项目许可：[Creative Commons Attribution-NonCommercial 4.0 International](https://github.com/giszh86/cn-math-taxonomy/blob/main/LICENSE)（CC BY-NC 4.0，署名、非商业使用）
- 本项目的修改：从原始题库中提取小学和初中的四选一、无图题；剔除依赖课文原文、选项重复或答案无法解析的题；映射成年级、学期、学科、题干、四个选项、答案及解析，合并到离线 JSON。题目内容可能被格式化以适应电视显示。
- 原项目与本应用没有官方关联或背书。使用这些题目时须遵守原项目许可，包括非商业使用、署名和标明修改。

## jagerzhang/PoHW：810 题

- 来源：[jagerzhang/PoHW](https://github.com/jagerzhang/PoHW) 经 [mastomato/PoHW-CN-K12](https://github.com/mastomato/PoHW-CN-K12) 合并的内置可作答题。
- 原项目代码采用 MIT 许可。其题目通过脚本生成，并使用 `RedSamerai/learning-content` 的知识点纲目；该纲目未提供 LICENSE。本项目保留来源说明，不将这部分题目声明为 MIT 数据。
- 本项目没有复制 PoHW 的 Android 应用代码。

## 本项目生成题：1,611 题

- 1,002 道数学计算与换算题、111 道科学常识题、498 道奥数思维题来自 PoHW-CN-K12 的参数化生成脚本。
- 题干由模板与数值生成，答案由程序计算或固定知识表给出。

题目仅供个人非商业学习使用。若要再发布或用于其他场景，请分别核对各来源许可。
