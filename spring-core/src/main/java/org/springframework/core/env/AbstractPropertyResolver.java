/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.core.env;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.util.SystemPropertyUtils;

/**
 * Abstract base class for resolving properties against any underlying source.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.1
 */
// 属性占位符的解析方法是PropertySourcesPropertyResolver的父类AbstractPropertyResolver
public abstract class AbstractPropertyResolver implements ConfigurablePropertyResolver {

	protected final Log logger = LogFactory.getLog(getClass());

	@Nullable
	private volatile ConfigurableConversionService conversionService;
    // //ignoreUnresolvableNestedPlaceholders=true情况下创建的PropertyPlaceholderHelper实例
	@Nullable
	private PropertyPlaceholderHelper nonStrictHelper;
	//ignoreUnresolvableNestedPlaceholders=false情况下创建的PropertyPlaceholderHelper实例
	@Nullable
	private PropertyPlaceholderHelper strictHelper;
    // ignoreUnresolvableNestedPlaceholders属性默认为false
	private boolean ignoreUnresolvableNestedPlaceholders = false;
	//属性占位符前缀，这里是"${"
	private String placeholderPrefix = SystemPropertyUtils.PLACEHOLDER_PREFIX;
	//属性占位符后缀，这里是"}"
	private String placeholderSuffix = SystemPropertyUtils.PLACEHOLDER_SUFFIX;
	//属性占位符解析失败的时候配置默认值的分隔符，这里是":"
	@Nullable
	private String valueSeparator = SystemPropertyUtils.VALUE_SEPARATOR;

	private final Set<String> requiredProperties = new LinkedHashSet<>();


	@Override
	public ConfigurableConversionService getConversionService() {
		// Need to provide an independent DefaultConversionService, not the
		// shared DefaultConversionService used by PropertySourcesPropertyResolver.
		ConfigurableConversionService cs = this.conversionService;
		if (cs == null) {
			synchronized (this) {
				cs = this.conversionService;
				if (cs == null) {
					cs = new DefaultConversionService();
					this.conversionService = cs;
				}
			}
		}
		return cs;
	}

	@Override
	public void setConversionService(ConfigurableConversionService conversionService) {
		Assert.notNull(conversionService, "ConversionService must not be null");
		this.conversionService = conversionService;
	}

	/**
	 * Set the prefix that placeholders replaced by this resolver must begin with.
	 * <p>The default is "${".
	 * @see org.springframework.util.SystemPropertyUtils#PLACEHOLDER_PREFIX
	 */
	@Override
	public void setPlaceholderPrefix(String placeholderPrefix) {
		Assert.notNull(placeholderPrefix, "'placeholderPrefix' must not be null");
		this.placeholderPrefix = placeholderPrefix;
	}

	/**
	 * Set the suffix that placeholders replaced by this resolver must end with.
	 * <p>The default is "}".
	 * @see org.springframework.util.SystemPropertyUtils#PLACEHOLDER_SUFFIX
	 */
	@Override
	public void setPlaceholderSuffix(String placeholderSuffix) {
		Assert.notNull(placeholderSuffix, "'placeholderSuffix' must not be null");
		this.placeholderSuffix = placeholderSuffix;
	}

	/**
	 * Specify the separating character between the placeholders replaced by this
	 * resolver and their associated default value, or {@code null} if no such
	 * special character should be processed as a value separator.
	 * <p>The default is ":".
	 * @see org.springframework.util.SystemPropertyUtils#VALUE_SEPARATOR
	 */
	@Override
	public void setValueSeparator(@Nullable String valueSeparator) {
		this.valueSeparator = valueSeparator;
	}

	/**
	 * Set whether to throw an exception when encountering an unresolvable placeholder
	 * nested within the value of a given property. A {@code false} value indicates strict
	 * resolution, i.e. that an exception will be thrown. A {@code true} value indicates
	 * that unresolvable nested placeholders should be passed through in their unresolved
	 * ${...} form.
	 * <p>The default is {@code false}.
	 * @since 3.2
	 */
	@Override
	public void setIgnoreUnresolvableNestedPlaceholders(boolean ignoreUnresolvableNestedPlaceholders) {
		this.ignoreUnresolvableNestedPlaceholders = ignoreUnresolvableNestedPlaceholders;
	}

	@Override
	public void setRequiredProperties(String... requiredProperties) {
		Collections.addAll(this.requiredProperties, requiredProperties);
	}

	@Override
	public void validateRequiredProperties() {
		MissingRequiredPropertiesException ex = new MissingRequiredPropertiesException();
		for (String key : this.requiredProperties) {
			if (this.getProperty(key) == null) {
				ex.addMissingRequiredProperty(key);
			}
		}
		if (!ex.getMissingRequiredProperties().isEmpty()) {
			throw ex;
		}
	}

	@Override
	public boolean containsProperty(String key) {
		return (getProperty(key) != null);
	}

	@Override
	@Nullable
	public String getProperty(String key) {
		return getProperty(key, String.class);
	}

	@Override
	public String getProperty(String key, String defaultValue) {
		String value = getProperty(key);
		return (value != null ? value : defaultValue);
	}

	@Override
	public <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
		T value = getProperty(key, targetType);
		return (value != null ? value : defaultValue);
	}

	@Override
	public String getRequiredProperty(String key) throws IllegalStateException {
		String value = getProperty(key);
		if (value == null) {
			throw new IllegalStateException("Required key '" + key + "' not found");
		}
		return value;
	}

	@Override
	public <T> T getRequiredProperty(String key, Class<T> valueType) throws IllegalStateException {
		T value = getProperty(key, valueType);
		if (value == null) {
			throw new IllegalStateException("Required key '" + key + "' not found");
		}
		return value;
	}

	@Override
	public String resolvePlaceholders(String text) {
		if (this.nonStrictHelper == null) {
			this.nonStrictHelper = createPlaceholderHelper(true);
		}
		return doResolvePlaceholders(text, this.nonStrictHelper);
	}

	@Override
	public String resolveRequiredPlaceholders(String text) throws IllegalArgumentException {
		if (this.strictHelper == null) {
			this.strictHelper = createPlaceholderHelper(false);
		}
		return doResolvePlaceholders(text, this.strictHelper);
	}

	/**
	 * Resolve placeholders within the given string, deferring to the value of
	 * {@link #setIgnoreUnresolvableNestedPlaceholders} to determine whether any
	 * unresolvable placeholders should raise an exception or be ignored.
	 * <p>Invoked from {@link #getProperty} and its variants, implicitly resolving
	 * nested placeholders. In contrast, {@link #resolvePlaceholders} and
	 * {@link #resolveRequiredPlaceholders} do <i>not</i> delegate
	 * to this method but rather perform their own handling of unresolvable
	 * placeholders, as specified by each of those methods.
	 * @since 3.2
	 * @see #setIgnoreUnresolvableNestedPlaceholders
	 */
	protected String resolveNestedPlaceholders(String value) {
		if (value.isEmpty()) {
			return value;
		}
		// ignoreUnresolvableNestedPlaceholders属性默认为false，
		// 可以通过AbstractEnvironment#setIgnoreUnresolvableNestedPlaceholders(boolean ignoreUnresolvableNestedPlaceholders)设置，
		// 当此属性被设置为true，解析属性占位符失败的时候(并且没有为占位符配置默认值)不会抛出异常，返回属性原样字符串，
		// 否则会抛出IllegalArgumentException。
		return (this.ignoreUnresolvableNestedPlaceholders ?
				resolvePlaceholders(value) : resolveRequiredPlaceholders(value));
	}
	//创建一个新的PropertyPlaceholderHelper实例，这里ignoreUnresolvablePlaceholders为false
	private PropertyPlaceholderHelper createPlaceholderHelper(boolean ignoreUnresolvablePlaceholders) {
		return new PropertyPlaceholderHelper(this.placeholderPrefix, this.placeholderSuffix,
				this.valueSeparator, ignoreUnresolvablePlaceholders);
	}
	//这里最终的解析工作委托到PropertyPlaceholderHelper#replacePlaceholders完成

	/**
	 * 注意到这里的第一个参数text就是属性值的源字符串，
	 * 例如我们需要处理的属性为myProperties: ${server.port}-${spring.application.name}，
	 * 这里的text就是${server.port}-${spring.application.name}。
	 *
	 * replacePlaceholders方法的第二个参数placeholderResolver，
	 * 这里的方法引用this::getPropertyAsRawString
	 *
	 */
	private String doResolvePlaceholders(String text, PropertyPlaceholderHelper helper) {
		return helper.replacePlaceholders(text, this::getPropertyAsRawString);
	}

	/**
	 * Convert the given value to the specified target type, if necessary.
	 * @param value the original property value
	 * @param targetType the specified target type for property retrieval
	 * @return the converted value, or the original value if no conversion
	 * is necessary
	 * @since 4.3.5
	 */
	// 属性字符串值，可以把字符串转换为指定的类型，此功能由AbstractPropertyResolver#convertValueIfNecessary完成
	@SuppressWarnings("unchecked")
	@Nullable
	protected <T> T convertValueIfNecessary(Object value, @Nullable Class<T> targetType) {
		if (targetType == null) {
			return (T) value;
		}
		ConversionService conversionServiceToUse = this.conversionService;
		if (conversionServiceToUse == null) {
			// Avoid initialization of shared DefaultConversionService if
			// no standard type conversion is needed in the first place...
			// 这里一般只有字符串类型才会命中
			if (ClassUtils.isAssignableValue(targetType, value)) {
				return (T) value;
			}
			conversionServiceToUse = DefaultConversionService.getSharedInstance();
		}
		return conversionServiceToUse.convert(value, targetType);
	}


	/**
	 * Retrieve the specified property as a raw String,
	 * i.e. without resolution of nested placeholders.
	 * @param key the property name to resolve
	 * @return the property value or {@code null} if none found
	 */
	@Nullable
	protected abstract String getPropertyAsRawString(String key);

}
