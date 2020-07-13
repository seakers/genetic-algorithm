// AUTO-GENERATED FILE. DO NOT MODIFY.
//
// This class was automatically generated by Apollo GraphQL plugin from the GraphQL queries it found.
// It should not be modified by hand.
//
package com.algorithm;

import com.algorithm.type.CustomType;
import com.apollographql.apollo.api.Operation;
import com.apollographql.apollo.api.OperationName;
import com.apollographql.apollo.api.Query;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.api.ResponseField;
import com.apollographql.apollo.api.ScalarTypeAdapters;
import com.apollographql.apollo.api.internal.InputFieldMarshaller;
import com.apollographql.apollo.api.internal.InputFieldWriter;
import com.apollographql.apollo.api.internal.OperationRequestBodyComposer;
import com.apollographql.apollo.api.internal.QueryDocumentMinifier;
import com.apollographql.apollo.api.internal.ResponseFieldMapper;
import com.apollographql.apollo.api.internal.ResponseFieldMarshaller;
import com.apollographql.apollo.api.internal.ResponseReader;
import com.apollographql.apollo.api.internal.ResponseWriter;
import com.apollographql.apollo.api.internal.SimpleOperationResponseParser;
import com.apollographql.apollo.api.internal.UnmodifiableMapBuilder;
import com.apollographql.apollo.api.internal.Utils;
import java.io.IOException;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import okio.Buffer;
import okio.BufferedSource;
import okio.ByteString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ArchitectureQuery implements Query<ArchitectureQuery.Data, ArchitectureQuery.Data, ArchitectureQuery.Variables> {
  public static final String OPERATION_ID = "2b64913a07f67de82dabbf25c19405abf80f110bf3d0c21a451803c5ddb798fd";

  public static final String QUERY_DOCUMENT = QueryDocumentMinifier.minify(
    "query Architecture($problem_id: Int!) {\n"
        + "  items: Architecture(where: {problem_id: {_eq: $problem_id}, eval_status: {_eq: true}}) {\n"
        + "    __typename\n"
        + "    id\n"
        + "    input\n"
        + "    science\n"
        + "    cost\n"
        + "  }\n"
        + "}"
  );

  public static final OperationName OPERATION_NAME = new OperationName() {
    @Override
    public String name() {
      return "Architecture";
    }
  };

  private final ArchitectureQuery.Variables variables;

  public ArchitectureQuery(int problem_id) {
    variables = new ArchitectureQuery.Variables(problem_id);
  }

  @Override
  public String operationId() {
    return OPERATION_ID;
  }

  @Override
  public String queryDocument() {
    return QUERY_DOCUMENT;
  }

  @Override
  public ArchitectureQuery.Data wrapData(ArchitectureQuery.Data data) {
    return data;
  }

  @Override
  public ArchitectureQuery.Variables variables() {
    return variables;
  }

  @Override
  public ResponseFieldMapper<ArchitectureQuery.Data> responseFieldMapper() {
    return new Data.Mapper();
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public OperationName name() {
    return OPERATION_NAME;
  }

  @Override
  @NotNull
  public Response<ArchitectureQuery.Data> parse(@NotNull final BufferedSource source,
      @NotNull final ScalarTypeAdapters scalarTypeAdapters) throws IOException {
    return SimpleOperationResponseParser.parse(source, this, scalarTypeAdapters);
  }

  @Override
  @NotNull
  public Response<ArchitectureQuery.Data> parse(@NotNull final ByteString byteString,
      @NotNull final ScalarTypeAdapters scalarTypeAdapters) throws IOException {
    return parse(new Buffer().write(byteString), scalarTypeAdapters);
  }

  @Override
  @NotNull
  public Response<ArchitectureQuery.Data> parse(@NotNull final BufferedSource source) throws
      IOException {
    return parse(source, ScalarTypeAdapters.DEFAULT);
  }

  @Override
  @NotNull
  public Response<ArchitectureQuery.Data> parse(@NotNull final ByteString byteString) throws
      IOException {
    return parse(byteString, ScalarTypeAdapters.DEFAULT);
  }

  @Override
  @NotNull
  public ByteString composeRequestBody(@NotNull final ScalarTypeAdapters scalarTypeAdapters) {
    return OperationRequestBodyComposer.compose(this, false, true, scalarTypeAdapters);
  }

  @NotNull
  @Override
  public ByteString composeRequestBody() {
    return OperationRequestBodyComposer.compose(this, false, true, ScalarTypeAdapters.DEFAULT);
  }

  @Override
  @NotNull
  public ByteString composeRequestBody(final boolean autoPersistQueries,
      final boolean withQueryDocument, @NotNull final ScalarTypeAdapters scalarTypeAdapters) {
    return OperationRequestBodyComposer.compose(this, autoPersistQueries, withQueryDocument, scalarTypeAdapters);
  }

  public static final class Builder {
    private int problem_id;

    Builder() {
    }

    public Builder problem_id(int problem_id) {
      this.problem_id = problem_id;
      return this;
    }

    public ArchitectureQuery build() {
      return new ArchitectureQuery(problem_id);
    }
  }

  public static final class Variables extends Operation.Variables {
    private final int problem_id;

    private final transient Map<String, Object> valueMap = new LinkedHashMap<>();

    Variables(int problem_id) {
      this.problem_id = problem_id;
      this.valueMap.put("problem_id", problem_id);
    }

    public int problem_id() {
      return problem_id;
    }

    @Override
    public Map<String, Object> valueMap() {
      return Collections.unmodifiableMap(valueMap);
    }

    @Override
    public InputFieldMarshaller marshaller() {
      return new InputFieldMarshaller() {
        @Override
        public void marshal(InputFieldWriter writer) throws IOException {
          writer.writeInt("problem_id", problem_id);
        }
      };
    }
  }

  public static class Data implements Operation.Data {
    static final ResponseField[] $responseFields = {
      ResponseField.forList("items", "Architecture", new UnmodifiableMapBuilder<String, Object>(1)
      .put("where", new UnmodifiableMapBuilder<String, Object>(2)
        .put("problem_id", new UnmodifiableMapBuilder<String, Object>(1)
          .put("_eq", new UnmodifiableMapBuilder<String, Object>(2)
            .put("kind", "Variable")
            .put("variableName", "problem_id")
            .build())
          .build())
        .put("eval_status", new UnmodifiableMapBuilder<String, Object>(1)
          .put("_eq", "true")
          .build())
        .build())
      .build(), false, Collections.<ResponseField.Condition>emptyList())
    };

    final @NotNull List<Item> items;

    private transient volatile String $toString;

    private transient volatile int $hashCode;

    private transient volatile boolean $hashCodeMemoized;

    public Data(@NotNull List<Item> items) {
      this.items = Utils.checkNotNull(items, "items == null");
    }

    /**
     * fetch data from the table: "Architecture"
     */
    public @NotNull List<Item> items() {
      return this.items;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public ResponseFieldMarshaller marshaller() {
      return new ResponseFieldMarshaller() {
        @Override
        public void marshal(ResponseWriter writer) {
          writer.writeList($responseFields[0], items, new ResponseWriter.ListWriter() {
            @Override
            public void write(List items, ResponseWriter.ListItemWriter listItemWriter) {
              for (Object item : items) {
                listItemWriter.writeObject(((Item) item).marshaller());
              }
            }
          });
        }
      };
    }

    @Override
    public String toString() {
      if ($toString == null) {
        $toString = "Data{"
          + "items=" + items
          + "}";
      }
      return $toString;
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      }
      if (o instanceof Data) {
        Data that = (Data) o;
        return this.items.equals(that.items);
      }
      return false;
    }

    @Override
    public int hashCode() {
      if (!$hashCodeMemoized) {
        int h = 1;
        h *= 1000003;
        h ^= items.hashCode();
        $hashCode = h;
        $hashCodeMemoized = true;
      }
      return $hashCode;
    }

    public static final class Mapper implements ResponseFieldMapper<Data> {
      final Item.Mapper itemFieldMapper = new Item.Mapper();

      @Override
      public Data map(ResponseReader reader) {
        final List<Item> items = reader.readList($responseFields[0], new ResponseReader.ListReader<Item>() {
          @Override
          public Item read(ResponseReader.ListItemReader listItemReader) {
            return listItemReader.readObject(new ResponseReader.ObjectReader<Item>() {
              @Override
              public Item read(ResponseReader reader) {
                return itemFieldMapper.map(reader);
              }
            });
          }
        });
        return new Data(items);
      }
    }
  }

  public static class Item {
    static final ResponseField[] $responseFields = {
      ResponseField.forString("__typename", "__typename", null, false, Collections.<ResponseField.Condition>emptyList()),
      ResponseField.forInt("id", "id", null, false, Collections.<ResponseField.Condition>emptyList()),
      ResponseField.forString("input", "input", null, true, Collections.<ResponseField.Condition>emptyList()),
      ResponseField.forCustomType("science", "science", null, true, CustomType.FLOAT8, Collections.<ResponseField.Condition>emptyList()),
      ResponseField.forCustomType("cost", "cost", null, true, CustomType.FLOAT8, Collections.<ResponseField.Condition>emptyList())
    };

    final @NotNull String __typename;

    final int id;

    final @Nullable String input;

    final @Nullable Object science;

    final @Nullable Object cost;

    private transient volatile String $toString;

    private transient volatile int $hashCode;

    private transient volatile boolean $hashCodeMemoized;

    public Item(@NotNull String __typename, int id, @Nullable String input,
        @Nullable Object science, @Nullable Object cost) {
      this.__typename = Utils.checkNotNull(__typename, "__typename == null");
      this.id = id;
      this.input = input;
      this.science = science;
      this.cost = cost;
    }

    public @NotNull String __typename() {
      return this.__typename;
    }

    public int id() {
      return this.id;
    }

    public @Nullable String input() {
      return this.input;
    }

    public @Nullable Object science() {
      return this.science;
    }

    public @Nullable Object cost() {
      return this.cost;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public ResponseFieldMarshaller marshaller() {
      return new ResponseFieldMarshaller() {
        @Override
        public void marshal(ResponseWriter writer) {
          writer.writeString($responseFields[0], __typename);
          writer.writeInt($responseFields[1], id);
          writer.writeString($responseFields[2], input);
          writer.writeCustom((ResponseField.CustomTypeField) $responseFields[3], science);
          writer.writeCustom((ResponseField.CustomTypeField) $responseFields[4], cost);
        }
      };
    }

    @Override
    public String toString() {
      if ($toString == null) {
        $toString = "Item{"
          + "__typename=" + __typename + ", "
          + "id=" + id + ", "
          + "input=" + input + ", "
          + "science=" + science + ", "
          + "cost=" + cost
          + "}";
      }
      return $toString;
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      }
      if (o instanceof Item) {
        Item that = (Item) o;
        return this.__typename.equals(that.__typename)
         && this.id == that.id
         && ((this.input == null) ? (that.input == null) : this.input.equals(that.input))
         && ((this.science == null) ? (that.science == null) : this.science.equals(that.science))
         && ((this.cost == null) ? (that.cost == null) : this.cost.equals(that.cost));
      }
      return false;
    }

    @Override
    public int hashCode() {
      if (!$hashCodeMemoized) {
        int h = 1;
        h *= 1000003;
        h ^= __typename.hashCode();
        h *= 1000003;
        h ^= id;
        h *= 1000003;
        h ^= (input == null) ? 0 : input.hashCode();
        h *= 1000003;
        h ^= (science == null) ? 0 : science.hashCode();
        h *= 1000003;
        h ^= (cost == null) ? 0 : cost.hashCode();
        $hashCode = h;
        $hashCodeMemoized = true;
      }
      return $hashCode;
    }

    public static final class Mapper implements ResponseFieldMapper<Item> {
      @Override
      public Item map(ResponseReader reader) {
        final String __typename = reader.readString($responseFields[0]);
        final int id = reader.readInt($responseFields[1]);
        final String input = reader.readString($responseFields[2]);
        final Object science = reader.readCustomType((ResponseField.CustomTypeField) $responseFields[3]);
        final Object cost = reader.readCustomType((ResponseField.CustomTypeField) $responseFields[4]);
        return new Item(__typename, id, input, science, cost);
      }
    }
  }
}
