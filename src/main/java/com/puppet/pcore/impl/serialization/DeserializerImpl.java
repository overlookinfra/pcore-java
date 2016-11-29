package com.puppet.pcore.impl.serialization;

import com.puppet.pcore.Default;
import com.puppet.pcore.Pcore;
import com.puppet.pcore.Sensitive;
import com.puppet.pcore.Type;
import com.puppet.pcore.impl.serialization.extension.*;
import com.puppet.pcore.impl.types.ObjectType;
import com.puppet.pcore.serialization.Deserializer;
import com.puppet.pcore.serialization.Reader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DeserializerImpl implements Deserializer {
	private final List<Object> objectsRead = new ArrayList<>();
	private final Pcore pcore;
	private final Reader reader;

	public DeserializerImpl(Pcore pcore, Reader reader) {
		this.pcore = pcore;
		this.reader = reader;
	}

	@Override
	public Object read() throws IOException {
		Object val = reader.read();
		if(val instanceof Tabulation)
			return objectsRead.get(((Tabulation)val).index);

		if(val == null || val instanceof Number || val instanceof String || val instanceof Boolean || val instanceof
				Default)
			return val;

		if(val instanceof MapStart) {
			int idx = ((MapStart)val).size;
			Map<Object,Object> result = remember(new LinkedHashMap<>());
			while(--idx >= 0) {
				Object key = read();
				result.put(key, read());
			}
			return result;
		}

		if(val instanceof ArrayStart) {
			int idx = ((ArrayStart)val).size;
			List<Object> result = remember(new ArrayList<>(idx));
			while(--idx >= 0)
				result.add(read());
			return result;
		}

		if(val instanceof SensitiveStart)
			return new Sensitive(read());

		if(val instanceof ObjectStart) {
			ObjectStart os = (ObjectStart)val;
			Type type = pcore.typeEvaluator().resolveType(os.typeName);
			if(!(type instanceof ObjectType))
				throw new SerializationException("No implementation mapping found for Puppet Type " + os.typeName);

			ObjectType ot = (ObjectType)type;
			return ot.newInstance(pcore, new DeserializerArgumentsAccessor(this, ot, os.attributeCount));
		}
		return remember(val);
	}

	<T> T remember(T value) {
		objectsRead.add(value);
		return value;
	}

	<T> void replacePlaceHolder(Object placeHolder, T createdInstance) {
		int idx = objectsRead.size();
		while(--idx >= 0) {
			if(objectsRead.get(idx) == placeHolder) {
				objectsRead.set(idx, createdInstance);
				return;
			}
		}
		throw new IllegalArgumentException("Attempt to replace non-existent place-holder");
	}
}
