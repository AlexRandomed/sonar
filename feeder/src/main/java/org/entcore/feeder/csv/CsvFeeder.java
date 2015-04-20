/*
 * Copyright © WebServices pour l'Éducation, 2015
 *
 * This file is part of ENT Core. ENT Core is a versatile ENT engine based on the JVM.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with ENT Core is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of ENT Core, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.entcore.feeder.csv;

import au.com.bytecode.opencsv.CSV;
import au.com.bytecode.opencsv.CSVReadProc;
import org.entcore.feeder.ManualFeeder;
import org.entcore.feeder.PartialFeed;
import org.entcore.feeder.dictionary.structures.DefaultFunctions;
import org.entcore.feeder.dictionary.structures.Importer;
import org.entcore.feeder.dictionary.structures.Structure;
import org.entcore.feeder.utils.Hash;
import org.entcore.feeder.utils.ResultMessage;
import org.entcore.feeder.utils.Validator;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import static org.entcore.feeder.be1d.Be1dFeeder.generateUserExternalId;
import static org.entcore.feeder.be1d.Be1dFeeder.frenchDatePatter;
import static org.entcore.feeder.dictionary.structures.DefaultProfiles.*;
import static org.entcore.feeder.dictionary.structures.DefaultProfiles.GUEST_PROFILE;
import static org.entcore.feeder.utils.FeederHelper.detectCharset;

public class CsvFeeder implements PartialFeed {

	private static final Logger log = LoggerFactory.getLogger(CsvFeeder.class);
	public static final long DEFAULT_STUDENT_SEED = 0l;
	private final Map<String, Object> namesMapping;

	public CsvFeeder(JsonObject additionnalsMappings) {
		JsonObject mappings = new JsonObject()
				.putString("id", "externalId")
				.putString("externalid", "externalId")
				.putString("nom", "lastName")
				.putString("prenom", "firstName")
				.putString("classe", "classes")
				.putString("idenfant", "childExternalId")
				.putString("datedenaissance", "birthDate")
				.putString("childid", "childExternalId")
				.putString("childexternalid", "childExternalId")
				.putString("nomenfant", "childLastName")
				.putString("prenomenfant", "childFirstName")
				.putString("classeenfant", "childClasses");

		mappings.mergeIn(additionnalsMappings);
		namesMapping = mappings.toMap();
	}

	@Override
	public void launch(Importer importer, Handler<Message<JsonObject>> handler) throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getSource() {
		return "CSV";
	}

	@Override
	public void launch(final String profile, final String structureId, final String content, final String charset,
			final Importer importer, final Handler<Message<JsonObject>> handler) throws Exception {
		if (importer.isFirstImport()) {
			importer.profileConstraints();
			importer.functionConstraints();
			importer.structureConstraints();
			importer.fieldOfStudyConstraints();
			importer.moduleConstraints();
			importer.userConstraints();
			importer.classConstraints();
			importer.groupConstraints();
			importer.persist(new Handler<Message<JsonObject>>() {
				@Override
				public void handle(Message<JsonObject> message) {
					if (message != null && "ok".equals(message.body().getString("status"))) {
						start(profile, structureId, content, charset, importer, handler);
					} else {
						if (handler != null) {
							handler.handle(message);
						}
					}
				}
			});
		} else {
			start(profile, structureId, content, charset, importer, handler);
		}
	}

	public void start(final String profile, final String structureExternalId, String content, String charset,
			final Importer importer, final Handler<Message<JsonObject>> handler) {
		importer.createOrUpdateProfile(STUDENT_PROFILE);
		importer.createOrUpdateProfile(RELATIVE_PROFILE);
		importer.createOrUpdateProfile(PERSONNEL_PROFILE);
		importer.createOrUpdateProfile(TEACHER_PROFILE);
		importer.createOrUpdateProfile(GUEST_PROFILE);
		DefaultFunctions.createOrUpdateFunctions(importer);

		final Validator validator = ManualFeeder.profiles.get(profile);
		final Structure structure = importer.getStructure(structureExternalId);
		if (structure == null) {
			handler.handle(new ResultMessage().error("invalid.structure"));
			return;
		}

		CSV csvParser = CSV
				.ignoreLeadingWhiteSpace()
				.separator(';')
				.skipLines(0)
				.charset(charset)
				.create();
		final List<String> columns = new ArrayList<>();
		csvParser.readAndClose(new StringReader(content), new CSVReadProc() {
			@Override
			public void procRow(int i, String... strings) {
				if (i == 0) {
					for (int j = 0; j < strings.length; j++) {
						String cm = columnsNameMapping(strings[j]);
						if (namesMapping.containsValue(cm)) {
							columns.add(j, cm);
						} else {
							handler.handle(new ResultMessage().error("invalid.column " + cm));
							return;
						}
					}
				} else {
					JsonObject user = new JsonObject();
					user.putArray("structures", new JsonArray().add(structureExternalId));
					user.putArray("profiles", new JsonArray().add(profile));
					List<String[]> classes = new ArrayList<>();
					for (int j = 0; j < strings.length; j++) {
						final String c = columns.get(j);
						final String v = strings[j].trim();
						if (v.isEmpty()) continue;
						switch (validator.getType(c)) {
							case "string":
								if ("birthDate".equals(c)) {
									Matcher m = frenchDatePatter.matcher(v);
									if (m.find()) {
										user.putString(c, m.group(3) + "-" + m.group(2) + "-" + m.group(1));
									} else {
										user.putString(c, v);
									}
								} else {
									user.putString(c, v);
								}
								break;
							case "array-string":
								JsonArray a = user.getArray(c);
								if (a == null) {
									a = new JsonArray();
									user.putArray(c, a);
								}
								a.add(v);
								break;
							case "boolean":
								user.putBoolean(c, "true".equals(v.toLowerCase()));
								break;
							default:
								Object o = user.getValue(c);
								if (o != null) {
									if (o instanceof JsonArray) {
										((JsonArray) o).add(v);
									} else {
										JsonArray array = new JsonArray();
										array.add(o).add(v);
										user.putArray(c, array);
									}
								} else {
									user.putString(c, v);
								}
						}
						if ("classes".equals(c)) {
							String eId = structure.getExternalId() + '$' + v;
							structure.createClassIfAbsent(eId, v);
							String[] classId = new String[2];
							classId[0] = structure.getExternalId();
							classId[1] = eId;
							classes.add(classId);
						}
					}
					String ca;
					long seed;
					JsonArray classesA;
					Object co = user.getValue("classes");
					if (co != null && co instanceof JsonArray) {
						classesA = (JsonArray) co;
					} else if (co instanceof String) {
						classesA = new JsonArray().add(co);
					} else {
						classesA = null;
					}
					if ("Student".equals(profile) && classesA != null && classesA.size() == 1) {
						seed = DEFAULT_STUDENT_SEED;
						ca = classesA.get(0);
					} else {
						ca = String.valueOf(i);
						seed = System.currentTimeMillis();
					}
					generateUserExternalId(user, ca, structure, seed);
					switch (profile) {
						case "Teacher":
							importer.createOrUpdatePersonnel(user, TEACHER_PROFILE_EXTERNAL_ID,
									user.getArray("structures"), classes.toArray(new String[classes.size()][2]),
									null, true, true);
							break;
						case "Personnel":
							importer.createOrUpdatePersonnel(user, PERSONNEL_PROFILE_EXTERNAL_ID,
									user.getArray("structures"), classes.toArray(new String[classes.size()][2]),
									null, true, true);
							break;
						case "Student":
							importer.createOrUpdateStudent(user, STUDENT_PROFILE_EXTERNAL_ID, null, null,
									classes.toArray(new String[classes.size()][2]), null, null, true, true);
							break;
						case "Relative":
							JsonArray linkStudents = new JsonArray();
							for (String attr : user.getFieldNames()) {
								if ("childExternalId".equals(attr)) {
									Object o = user.getValue(attr);
									if (o instanceof JsonArray) {
										for (Object c: (JsonArray)o) {
											linkStudents.add(c);
										}
									} else {
										linkStudents.add(o);
									}
								} else if ("childLastName".equals(attr)) {
									Object childLastName = user.getValue(attr);
									Object childFirstName = user.getValue("childFirstName");
									Object childClasses = user.getValue("childClasses");
									if (childLastName instanceof JsonArray && childFirstName instanceof JsonArray &&
											childClasses instanceof JsonArray &&
											((JsonArray) childClasses).size() == ((JsonArray) childLastName).size() &&
											((JsonArray) childFirstName).size() == ((JsonArray) childLastName).size()) {
										for (int j = 0; j < ((JsonArray) childLastName).size(); j++) {
											String mapping = structure.getExternalId()+
													((JsonArray) childLastName).<String>get(i).trim()+
													((JsonArray) childFirstName).<String>get(i).trim()+
													((JsonArray) childClasses).<String>get(i).trim()+DEFAULT_STUDENT_SEED;
											relativeStudentMapping(linkStudents, mapping);
										}
									} else if (childLastName instanceof String && childFirstName instanceof String &&
											childClasses instanceof String) {
										if(childLastName != null && childFirstName != null && childClasses != null) {
											String mapping = structure.getExternalId() +
													childLastName.toString().trim() +
													childFirstName.toString().trim() +
													childClasses.toString().trim() + DEFAULT_STUDENT_SEED;
											relativeStudentMapping(linkStudents, mapping);
										}
									} else {
										handler.handle(new ResultMessage().error("invalid.child.mapping"));
										return;
									}
								}
							}
							importer.createOrUpdateUser(user, linkStudents);
							break;
						case "Guest":
							importer.createOrUpdateGuest(user, classes.toArray(new String[classes.size()][2]));
							break;
					}
				}
			}

			private void relativeStudentMapping(JsonArray linkStudents, String mapping) {
				if (mapping.trim().isEmpty()) return;
				try {
					linkStudents.add(Hash.sha1(mapping.getBytes("UTF-8")));
				} catch (NoSuchAlgorithmException |UnsupportedEncodingException e) {
					log.error(e.getMessage(), e);
				}
			}

		});
		switch (profile) {
			case "Relative":
				importer.linkRelativeToClass(RELATIVE_PROFILE_EXTERNAL_ID);
				importer.linkRelativeToStructure(RELATIVE_PROFILE_EXTERNAL_ID);
				break;
		}
//		importer.markMissingUsers(structure.getExternalId(), new Handler<Void>() {
//			@Override
//			public void handle(Void event) {
//				importer.persist(handler);
//			}
//		});
		importer.persist(handler);
	}

	private String columnsNameMapping(String columnName) {
		final String key = Validator.removeAccents(columnName.trim().toLowerCase()).replaceAll("\\s+", "");
		final Object attr = namesMapping.get(key);
		return attr != null ? attr.toString() : key;
	}

}
