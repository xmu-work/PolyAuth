# 项目本地 Maven 仓库

该目录用于保存无法从公共 Maven 仓库获取的课程依赖。

`cn.edu.xmu:clonefactory:0.0.2` 来自课程 CloneFactory 注解处理器源码，
为兼容本项目的 Java 17 基线，处理器源码仅将声明版本从 Java 21 调整为 Java 17 后重新编译。
JAR 保留原始包名 `cn.edu.xmu.clonefactory`，用于编译期生成
`target/generated-sources/annotations/cn/edu/xmu/clonefactory/util/CloneFactory.java`。
