# Magic Storage

只要在类或 record 上标注 `@GenStore`，注解处理器就会在编译期生成一个对应的 `XxxStore` 类。该类将每个元素拆解为原始类型数组，并提供类型安全的读写方法。生成的数组均为 `public final` 字段，可以直接用于 GPU 缓冲区、NIO 的 `ByteBuffer` / `FloatBuffer`，或任何需要原始数组的代码。

## 特性

- **SoA / AoS 通用**：默认按结构数组（SoA）布局，每个字段（组）对应一条原始类型数组。
- **支持 Record 和普通类**：record 使用规范构造函数 + 访问器；普通类要求字段全部为 `public` 且非 `final`、非 `static`。
- **复合类型拆解**：支持把 JOML 的 `Vector` / `Matrix` / `Quaternion` 等类型拆成标量数组（`@Joml` / `@Glue`），也支持把自定义的 `@MutableClassSource` 类拆成数组。
- **自定义分组**：通过 `@Field(group = "...")` 把字段分配到不同的数组，可分别传输到多个 VBO 。
- **零运行时依赖**：在编译时生成 Java 代码，不依赖运行时反射。在运行时可以不依赖本库。

## 快速开始

```gradle
dependencies {
    implementation 'yee.pltision:magic-storage:1.0-SNAPSHOT'
    annotationProcessor 'yee.pltision:magic-storage:1.0-SNAPSHOT'
}
```

然后在类或 record 上标注 `@GenStore` 即可。

## 基本用法

### Record

```java
@GenStore
public record Vertex(
        float x,
        float y,
        float z,
        @Field(group = "rgba") float r,
        @Field(group = "rgba") float g,
        @Field(group = "rgba") float b,
        @Field(group = "rgba") float a
) {}
```

### 普通类

```java
@GenStore
public class Sprite {
    public Vector position;
    public Vector velocity;
    public float scale;
    @Field(group = "i")
    public int alpha;
}
```

### 生成的内容

以 `Vertex` 为例会生成 `VertexStore`：

```java
VertexStore store = new VertexStore(100);   // 容量 100

// 每条数组（可以直接传给 GPU / NIO / 其他代码）
store.vertexArray;     // float[]
store.rgbaArray;       // float[]

// 常量：大小、偏移、每条字段的尺寸
VertexStore.VERTEX_SIZE;   // 3
VertexStore.RGBA_SIZE;     // 4
VertexStore.X_OFFSET;      // 0
VertexStore.A_OFFSET;      // 0

// 单个字段读写
store.setX(0, 1.0f);
float x = store.getX(0);

// 整组写入
store.setVertex(0, 1.0f, 2.0f, 3.0f);

// 对象级读写
store.set(0, new Vertex(1.0f, 2.0f, 3.0f, 1, 1, 1, 1));
Vertex v = store.get(0);
```

生成的 `XxxStore` 具体包含：

| 成员 | 说明 |
| --- | --- |
| `public final int size` | 元素数量 |
| `public static final int XXX_SIZE` | 组大小（组内标量个数） |
| `public static final int FIELD_OFFSET` / `FIELD_SIZE` | 每个字段在组内的偏移 / 尺寸 |
| `public final <T>[] xxxArray` | 每组一条的原始类型数组 |
| `getField(i)` / `setField(i, v)` | 字段读写 |
| `getField(i, dest)` / `setField(i, x, y, ...)` | 复合类型的写入到目标对象 / 按标量写入 |
| `setXxx(i, ...)` | 整组写入 |
| `set(i, element)` / `get(i)` | 对象级读写 |

## 分组

默认所有字段合并到同一条数组（以类名首字母小写命名，如 `vertexArray`）。用 `@Field(group = "...")` 可以把字段分到不同的数组：

- **组名不能和字段名重名**。
- **同一个组里的字段必须是相同的原始类型**，否则报错。

例如上面的 `Sprite` 会生成 `spriteArray`（`float[]`，含 position / velocity / scale）和 `iArray`（`int[]`，含 alpha）两条数组。

## 复合类型

### JOML

JOML 的 `Vector`、`Matrix`、`Quaternion`、`AxisAngle` 等类型不是原始类型，需要拆成标量。给字段加上 `@Joml` 注解即可：

```java
@GenStore
public record Quad(
        @Joml Matrix4f transform,
        @Joml Vector4f texCoord,
        @Joml Vector4f rgba
) {}
```

`@Joml` 内部聚合了一系列 `@Glue`，描述每种 JOML 类型如何拆解、如何从数组重建。它会生成 `setTransform(i, m00, m01, ...)` 这样的标量写入方法，以及 `getTransform(i, dest)` 这样的复用对象方法。

> `@Joml` 由 `JomlGlueGenerator` 生成，由 `processor` 模块提供。

### 自定义 `@MutableClassSource`

想把自己的类作为复合类型拆进数组？给类标注 `@MutableClassSource`，然后在 `@GenStore` 的 record 字段里直接使用：

```java
@MutableClassSource
class Pos {
    public float x, y, z;
}

@MutableClassSource
class Rgba {
    public float r, g, b, a;
}

@GenStore
public record CompoundVertex(
        Pos position,
        Rgba color
) {}
```

生成的 `CompoundVertexStore` 会把 `position` 和 `color` 合并进同一条 `float[]`（前提是同类型），并提供 `getPosition(i)`、`setPosition(i, x, y, z)`、`getPosition(i, dest)` 等方法。

### 自定义 `@Glue`

`@Glue` 是更底层的机制，可以给任意类型定义拆解模板（构造、写入目标对象、标量写入的代码模板）。也可以仿照 `@Joml` 把多个 `@Glue` 聚合进一个自定义注解，处理器会递归查找。具体见 `Glue` 注解的 javadoc。

## 限制

当前版本（1.0-SNAPSHOT）有以下限制，部分符合现在的设计意图：

- **`@MutableClassSource` 的所有字段必须同类型**，复合类型也许在设计意图之外。如果不打算支持紧密排布一些复合类型或者特殊编码方式，就不应使用此功能。
- **组内字段必须是同一种类型**，因为组代表的就是一个类型的数组。

## 未实现

当前版本（1.0-SNAPSHOT）有以下未实现的功能：

- **只支持 “只有 public 字段” 的类和 record**：`@GenStore` 的普通类只检索 `public` 且非 `final`、非 `static`的字段；record 直接使用访问器。
  预计支持使用访问器和规范构造函数（即允许 private 字段 + 访问器的类）。
- **`@MutableClassSource` 不支持嵌套**：`@MutableClassSource` 类的字段必须是原始类型（或 JOML 等可通过 `@Glue` 拆解的类型），不能再是另一个 `@MutableClassSource`。
- **预期支持 Buffer**：堆内数组不能让 NIO 零拷贝访问，虽然并不难做但我暂时懒了。


## 模块结构

```
processor/   注解处理器本体
  annotation/     @GenStore、@Field、@Glue、@Joml、@WithAccessor
  compoundsource/ @MutableClassSource、@CompoundSource
  joml/           JomlGlueGenerator（生成 @Joml 的代码生成器）
src/test/   使用示例与单元测试
```
