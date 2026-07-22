package yee.pltision.soa.processor.spi;

import com.palantir.javapoet.TypeName;
import yee.pltision.soa.processor.FieldCodeBlock;

import java.util.Map;

/**
 * 提供 {@code TypeName} 到 {@link FieldCodeBlock} 映射的 SPI。
 * 任何胶水库只需实现此接口并通过 {@link java.util.ServiceLoader} 注册即可被处理器自动发现。
 */
public interface ElementGlueProvider {

    /**
     * 返回一个不可变（或可变但不影响）的映射，键为字段类型（如 Vector3f），值为对应的 FieldCodeBlock。
     * 处理器会在初始化时调用一次并缓存。
     */
    Map<TypeName, FieldCodeBlock> getElementMap();
}