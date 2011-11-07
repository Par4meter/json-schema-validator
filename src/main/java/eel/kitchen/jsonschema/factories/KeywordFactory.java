/*
 * Copyright (c) 2011, Francis Galiegue <fgaliegue@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the Lesser GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package eel.kitchen.jsonschema.factories;

import eel.kitchen.jsonschema.ValidationReport;
import eel.kitchen.jsonschema.base.AlwaysFalseValidator;
import eel.kitchen.jsonschema.base.AlwaysTrueValidator;
import eel.kitchen.jsonschema.base.MatchAllValidator;
import eel.kitchen.jsonschema.base.Validator;
import eel.kitchen.jsonschema.container.ArrayValidator;
import eel.kitchen.jsonschema.container.ObjectValidator;
import eel.kitchen.jsonschema.context.ValidationContext;
import eel.kitchen.jsonschema.keyword.AdditionalItemsValidator;
import eel.kitchen.jsonschema.keyword.AdditionalPropertiesValidator;
import eel.kitchen.jsonschema.keyword.AlwaysTrueKeywordValidator;
import eel.kitchen.jsonschema.keyword.DependenciesValidator;
import eel.kitchen.jsonschema.keyword.DisallowValidator;
import eel.kitchen.jsonschema.keyword.DivisibleByValidator;
import eel.kitchen.jsonschema.keyword.EnumValidator;
import eel.kitchen.jsonschema.keyword.ExtendsValidator;
import eel.kitchen.jsonschema.keyword.FormatValidator;
import eel.kitchen.jsonschema.keyword.KeywordValidator;
import eel.kitchen.jsonschema.keyword.MaxItemsValidator;
import eel.kitchen.jsonschema.keyword.MaxLengthValidator;
import eel.kitchen.jsonschema.keyword.MaximumValidator;
import eel.kitchen.jsonschema.keyword.MinItemsValidator;
import eel.kitchen.jsonschema.keyword.MinLengthValidator;
import eel.kitchen.jsonschema.keyword.MinimumValidator;
import eel.kitchen.jsonschema.keyword.PatternValidator;
import eel.kitchen.jsonschema.keyword.PropertiesValidator;
import eel.kitchen.jsonschema.keyword.RefValidator;
import eel.kitchen.jsonschema.keyword.TypeValidator;
import eel.kitchen.jsonschema.keyword.UniqueItemsValidator;
import eel.kitchen.util.CollectionUtils;
import eel.kitchen.util.NodeType;
import org.codehaus.jackson.JsonNode;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static eel.kitchen.util.NodeType.*;

/**
 * Factory for keyword validators, ie the core of validation.
 */
public final class KeywordFactory
{
    /**
     * Map pairing a schema keyword and the instance types it applies to
     */
    private final Map<String, EnumSet<NodeType>> fieldMap
        = new HashMap<String, EnumSet<NodeType>>();

    /**
     * Map pairing a schema keyword and the matching {@link KeywordValidator}
     * as a {@link Class}
     */
    private final Map<String, Class<? extends KeywordValidator>> validators
        = new HashMap<String, Class<? extends KeywordValidator>>();

    /**
     * Constructor; registers validators using
     * {@link #registerValidator(String, Class, NodeType...)}.
     */
    public KeywordFactory()
    {
        registerValidator("additionalItems", AdditionalItemsValidator.class,
            ARRAY);
        registerValidator("additionalProperties",
            AdditionalPropertiesValidator.class, OBJECT);
        registerValidator("dependencies", DependenciesValidator.class,
            NodeType.values());
        registerValidator("disallow", DisallowValidator.class,
            NodeType.values());
        registerValidator("divisibleBy", DivisibleByValidator.class, INTEGER,
            NUMBER);
        registerValidator("enum", EnumValidator.class, NodeType.values());
        registerValidator("extends", ExtendsValidator.class, NodeType.values());
        registerValidator("format", FormatValidator.class, NodeType.values());
        registerValidator("items", AlwaysTrueKeywordValidator.class, ARRAY);
        registerValidator("maximum", MaximumValidator.class, INTEGER, NUMBER);
        registerValidator("maxItems", MaxItemsValidator.class, ARRAY);
        registerValidator("maxLength", MaxLengthValidator.class, STRING);
        registerValidator("minimum", MinimumValidator.class, INTEGER, NUMBER);
        registerValidator("minItems", MinItemsValidator.class, ARRAY);
        registerValidator("minLength", MinLengthValidator.class, STRING);
        registerValidator("pattern", PatternValidator.class, STRING);
        registerValidator("patternProperties", AlwaysTrueKeywordValidator.class,
            OBJECT);
        registerValidator("properties", PropertiesValidator.class,
            OBJECT);
        registerValidator("type", TypeValidator.class, NodeType.values());
        registerValidator("uniqueItems", UniqueItemsValidator.class, ARRAY);
        registerValidator("$ref", RefValidator.class, NodeType.values());
    }

    /**
     * Register one validator for a given keyword
     *
     * @param field the keyword
     * @param v the {@link KeywordValidator} as a {@link Class} object
     * @param types the instance types this validator can handle
     */
    private void registerValidator(final String field,
        final Class<? extends KeywordValidator> v, final NodeType... types)
    {
        final EnumSet<NodeType> typeset = EnumSet.copyOf(Arrays.asList(types));

        fieldMap.put(field, typeset);
        validators.put(field, v);
    }

    /**
     * Get a validator (a {@link KeywordValidator} really) for the given
     * context and instance to validate. Only called from {@link
     * ValidationContext#getValidator(JsonNode)}.
     *
     * @param context the current validation context
     * @param instance the instance to validate
     * @return the validator
     */
    public Validator getValidator(final ValidationContext context,
        final JsonNode instance)
    {
        final Collection<KeywordValidator> collection
            = getValidators(context, instance);


        final KeywordValidator validator;
        switch (collection.size()) {
            case 0:
                return new AlwaysTrueKeywordValidator(context, instance);
            case 1:
                validator = collection.iterator().next();
                break;
            default:
                validator = new MatchAllValidator(context, collection);
        }

        if (!instance.isContainerNode())
            return validator;

        return instance.isArray()
            ? new ArrayValidator(validator, context, instance)
            : new ObjectValidator(validator, context, instance);
    }


    /**
     * Get a collection of validators for the context and instance,
     * by grabbing the schema node using
     * {@link ValidationContext#getSchemaNode()} and grabbing validators from
     * the {@link #fieldMap} and {@link #validators} maps. Will return an
     * {@link AlwaysTrueValidator} if no validators are found (ie,
     * none of the keywords of the schema node can validate the instance
     * type), and an {@link AlwaysFalseValidator} if one validator fails to
     * instantiate (see
     * {@link #buildValidator(Class, ValidationContext, JsonNode)}).
     *
     * @param context the validation context
     * @param instance the instance
     * @return the list of validators as a {@link Collection}
     */
    private Collection<KeywordValidator> getValidators(
        final ValidationContext context, final JsonNode instance)
    {
        final NodeType type = NodeType.getNodeType(instance);
        final Set<KeywordValidator> ret = new LinkedHashSet<KeywordValidator>();

        final JsonNode schemaNode = context.getSchemaNode();

        final Set<String> keywords
            = CollectionUtils.toSet(schemaNode.getFieldNames());

        if (keywords.isEmpty())
            return Arrays.<KeywordValidator>asList(
                new AlwaysTrueKeywordValidator(context, instance));

        final Set<String> keyset = new HashSet<String>();

        Class<? extends KeywordValidator> c;

        keyset.addAll(validators.keySet());
        keyset.retainAll(keywords);

        KeywordValidator validator;

        for (final String key: keyset) {
            if (!fieldMap.get(key).contains(type))
                continue;
            c = validators.get(key);
            try {
                validator = buildValidator(c, context, instance);
                ret.add(validator);
            } catch (Exception e) {
                final String message = "Cannot instantiate validator "
                    + "for keyword " + key + ": " + e.getClass().getName();
                final ValidationReport report = context.createReport();
                report.addMessage(message);
                validator = new AlwaysFalseValidator(report);
                return Arrays.asList(validator);
            }
        }

        return Collections.unmodifiableSet(ret);
    }

    /**
     * Build a validator given a class, context and instance.
     *
     * @param c the class object
     * @param context the context
     * @param instance the instance
     * @return the validator
     * @throws NoSuchMethodException constructor was not found
     * @throws InvocationTargetException see {@link InvocationTargetException}
     * @throws IllegalAccessException see {@link IllegalAccessException}
     * @throws InstantiationException see {@link InstantiationException}
     */
    private static KeywordValidator buildValidator(
        final Class<? extends KeywordValidator> c,
        final ValidationContext context, final JsonNode instance)
        throws NoSuchMethodException, InvocationTargetException,
        IllegalAccessException, InstantiationException
    {
        final Constructor<? extends KeywordValidator> constructor
            = c.getConstructor(ValidationContext.class, JsonNode.class);

        return constructor.newInstance(context, instance);
    }
}
