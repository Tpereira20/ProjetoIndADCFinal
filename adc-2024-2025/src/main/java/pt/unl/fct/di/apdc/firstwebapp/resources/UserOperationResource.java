package pt.unl.fct.di.apdc.firstwebapp.resources;

import java.util.*;
import java.util.logging.Logger;

import com.google.cloud.Timestamp;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import org.apache.commons.codec.digest.DigestUtils;
import pt.unl.fct.di.apdc.firstwebapp.util.*;

import com.google.cloud.datastore.*;

import com.google.gson.Gson;

import javax.print.attribute.standard.Media;


@Path("/begin")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class UserOperationResource {

	private static final int ONE_HOUR = 3600000;

	private static final String LOG_MESSAGE_LOGIN_ATTEMP = "Login attempt by user: ";
	private static final String MESSAGE_INVALID_CREDENTIALS = "Incorrect username or password.";
	private static final String USER_PWD = "user_pwd";
	private static final String LOG_MESSAGE_LOGIN_SUCCESSFUL = "Login successful by user: ";
	private static final String LOG_MESSAGE_WRONG_PASSWORD = "Wrong password for: ";
	private static final String LOG_MESSAGE_UNKNOW_USER = "Failed login attempt for username: ";

	private static final Logger LOG = Logger.getLogger(UserOperationResource.class.getName());
	private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
	private static final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind("User");
	private static final KeyFactory tokenKeyFactory = datastore.newKeyFactory().setKind("Token");
	private final Gson g = new Gson();

	public UserOperationResource() {

	}

	@POST
	@Path("/login")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response doLogin(LoginData data) {
		LOG.fine(LOG_MESSAGE_LOGIN_ATTEMP + data.email);

		Key userKey = datastore.newKeyFactory().setKind("User").newKey(data.email);
		Entity user = datastore.get(userKey);

		if (user != null) {
			String hashedPWD = (String) user.getString(USER_PWD);
			if (hashedPWD.equals(DigestUtils.sha512Hex(data.password))) {
				LOG.info(LOG_MESSAGE_LOGIN_SUCCESSFUL + data.email);
				AuthToken token = new AuthToken(data.email);
				String role = user.getString("user_role");
				Key tokenKey = datastore.newKeyFactory().setKind("Token").newKey(token.tokenID);
				Entity tokenEnt = datastore.get(tokenKey);
				tokenEnt = Entity.newBuilder(tokenKey)
						.set("token_userid", data.email)
						.set("token_id", token.tokenID)
						.set("token_role", role)
						.set("token_valid_from", Timestamp.now())
						.set("token_valid_to", Timestamp.of(new Date(System.currentTimeMillis() + ONE_HOUR)))
						.build();
				datastore.put(tokenEnt);
				return Response.ok(g.toJson(token)).build();
			} else {
				LOG.warning(LOG_MESSAGE_WRONG_PASSWORD + data.email);
				return Response.status(Status.FORBIDDEN)
						.entity(MESSAGE_INVALID_CREDENTIALS)
						.build();
			}
		} else {
			LOG.warning(LOG_MESSAGE_UNKNOW_USER + data.email);
			return Response.status(Status.FORBIDDEN)
					.entity(MESSAGE_INVALID_CREDENTIALS)
					.build();
		}
	}

	@GET
	@Path("/{username}")
	public Response checkUsernameAvailable(@PathParam("username") String username) {
		return Response.ok().entity(g.toJson(true)).build();
	}


	@POST
	@Path("/register")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response registerUser(RegisterData data) {
		LOG.fine("Attempt to register user: " + data.username);

		if(!data.email.contains("@"))
			return Response.status(Status.BAD_REQUEST).entity("Invalid email address.").build();

		if(!data.phoneNumber.matches("^\\+[0-9]+$"))
			return Response.status(Status.BAD_REQUEST).entity("Invalid phone number.").build();

		if(!data.password.equals(data.confirmation))
			return Response.status(Status.BAD_REQUEST).entity("Invalid confirmation.").build();

		if(!(data.privacy.equals("público") || data.privacy.equals("privado")))
			return Response.status(Status.BAD_REQUEST).entity("Invalid privacy.").build();

		if(!data.validRegistration())
			return Response.status(Status.BAD_REQUEST).entity("Missing or wrong parameter.").build();

		Key userKey = datastore.newKeyFactory().setKind("User").newKey(data.email);
		Entity user = datastore.get(userKey);

		if(user != null)
			return Response.status(Status.BAD_REQUEST).entity("User already exists.").build();

		Entity.Builder userBuilder = Entity.newBuilder(userKey);

		userBuilder
				.set("user_email", data.email)
				.set("user_user_name", data.username)
				.set("user_full_name", data.name)
				.set("user_phone_number", data.phoneNumber)
				.set("user_pwd", DigestUtils.sha512Hex(data.password))
				.set("user_privacy", data.privacy)
				.build();

		if(data.role != null) {
			if(!(data.role.equals("admin") || data.role.equals("enduser") || data.role.equals("partner") || data.role.equals("backoffice")))
				return Response.status(Status.BAD_REQUEST).entity("Invalid role.").build();
			if(data.role.equals("admin")) {
				userBuilder.set("user_role", data.role);
				userBuilder.set("user_accountstate", "ativada");
			}
			if(data.role.equals("partner") || data.role.equals("backoffice"))
				userBuilder.set("user_role", data.role);
		} else {
			userBuilder.set("user_role", "enduser");
		}
		if(data.job != null)
			userBuilder.set("user_job", data.job);
		if(data.ccNumber != null && data.ccNumber.matches("\\d+"))
			userBuilder.set("user_ccnumber", data.ccNumber);
		if(data.nif != null)
			userBuilder.set("user_nif", data.nif);
		if(data.employer != null)
			userBuilder.set("user_employer", data.employer);
		if(data.address != null)
			userBuilder.set("user_address", data.address);
		if(data.employerNif != null && data.employerNif.matches("\\d+"))
			userBuilder.set("user_employernif", data.employerNif);
		if(data.accountState != null) {
			if(!(data.accountState.equals("ativada") || data.accountState.equals("desativada") || data.accountState.equals("suspensa")))
				return Response.status(Status.BAD_REQUEST).entity("Invalid account state.").build();
			if(data.accountState.equals("ativada") || data.accountState.equals("suspensa"))
				userBuilder.set("user_accountstate", data.accountState);
		} else {
			userBuilder.set("user_accountstate", "desativada");
		}

		user = userBuilder.build();


		datastore.put(user);
		LOG.info("User registered " + data.username);


		return Response.ok("User registered " +  data.username).build();
	}


	@POST
	@Path("/changerole")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response changeRole(@HeaderParam("Authorization") String tokenId, RoleData data) {
		LOG.fine("Attempt to change user role: " + data.role);

		if(!(data.role.equals("enduser") || data.role.equals("admin") || data.role.equals("backoffice") || data.role.equals("partner")))
			return Response.status(Status.BAD_REQUEST).entity("Invalid role.").build();

		Key userKey = datastore.newKeyFactory().setKind("User").newKey(data.username);
		Entity user = datastore.get(userKey);

		if(user == null) {
			Response.status(Status.BAD_REQUEST).entity("User does not exist.").build();
		}

		Key tokenKey = datastore.newKeyFactory().setKind("Token").newKey(tokenId);
		Entity tokenEnt = datastore.get(tokenKey);

		if(tokenEnt != null) {
			if(data.username.equals(tokenEnt.getString("token_userid")))
				return Response.status(Status.BAD_REQUEST).entity("Can't change your own role...").build();

			if(data.role.equals(user.getString("user_role")))
				return Response.status(Status.BAD_REQUEST).entity("Same role as the current user role").build();

			if(tokenEnt.getString("token_role").equals("enduser") || tokenEnt.getString("token_role").equals("partner"))
				return Response.status(Status.BAD_REQUEST).entity(tokenEnt.getString("token_role")
						+ " can't change others roles...").build();

			if(!(tokenEnt.getString("token_role").equals("backoffice") && (user.getString("user_role").equals("admin")) ||
					user.getString("user_role").equals("backoffice") || data.role.equals("admin"))) {
				user = Entity.newBuilder(userKey)
								.set("user_role", data.role)
										.build();
				datastore.update(user);
				return Response.ok(g.toJson(tokenEnt)).build();
			} else if(tokenEnt.getString("token_role").equals("admin")) {
				user = Entity.newBuilder(userKey)
						.set("user_role", data.role)
						.build();
				datastore.update(user);
				return Response.ok(g.toJson(tokenEnt)).build();
			}else {
				return Response.status(Status.BAD_REQUEST).entity("Can't change admin role.").build();
			}
		}

		return Response.status(Status.BAD_REQUEST).entity("Not logged in...").build();
	}

	@POST
	@Path("/changestate")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response changeState(@HeaderParam("Authorization") String tokenId, RoleData data) {
		LOG.fine("Attempt to change account state: " + data.role);

		if(!(data.role.equals("desativada") || data.role.equals("ativada") || data.role.equals("suspensa")))
			return Response.status(Status.BAD_REQUEST).entity("Invalid account state.").build();

		Key userKey = datastore.newKeyFactory().setKind("User").newKey(data.username);
		Entity user = datastore.get(userKey);

		if(user == null) {
			Response.status(Status.BAD_REQUEST).entity("User does not exist.").build();
		}

		Key tokenKey = datastore.newKeyFactory().setKind("Token").newKey(tokenId);
		Entity tokenEnt = datastore.get(tokenKey);

		if(tokenEnt != null) {

			if(data.role.equals(user.getString("user_accountstate")))
				return Response.status(Status.BAD_REQUEST).entity("Same state as current state").build();

			if(tokenEnt.getString("token_role").equals("admin")) {
				user = Entity.newBuilder(userKey)
						.set("user_accountstate", data.role)
						.build();
				datastore.update(user);
				return Response.ok(g.toJson(tokenEnt)).build();
			} else if(tokenEnt.getString("token_role").equals("backoffice") && !data.role.equals("suspensa")) {
				user = Entity.newBuilder(userKey)
						.set("user_accountstate", data.role)
						.build();
				datastore.update(user);
				return Response.ok(g.toJson(tokenEnt)).build();
			} else {
				return Response.status(Status.BAD_REQUEST).entity("Can't change account state.").build();
			}
		}

		return Response.status(Status.BAD_REQUEST).entity("Not logged in...").build();
	}



	@POST
	@Path("/removeaccount")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response changeRole(@HeaderParam("Authorization") String tokenId, RemoveData data) {
		LOG.fine("Attempt to delete user account: " + data.email);


		Key userKey = datastore.newKeyFactory().setKind("User").newKey(data.email);
		Entity user = datastore.get(userKey);

		if(user == null)
			return Response.status(Status.BAD_REQUEST).entity("User does not exist.").build();

		Key tokenKey = datastore.newKeyFactory().setKind("Token").newKey(tokenId);
		Entity tokenEnt = datastore.get(tokenKey);

		if(tokenEnt != null) {

			if(data.email.equals(tokenEnt.getString("token_userid")))
				return Response.status(Status.BAD_REQUEST).entity("Can't remove your own account...").build();

			if(!(tokenEnt.getString("token_role").equals("admin") || tokenEnt.getString("token_role").equals("backoffice")))
				return Response.status(Status.BAD_REQUEST).entity("Only admins or backoffice can remove account").build();

			if(tokenEnt.getString("token_role").equals("admin") || (tokenEnt.getString("token_role").equals("backoffice")
					&& !(user.getString("user_role").equals("admin")
					|| user.getString("user_role").equals("backoffice")))) {
				datastore.delete(userKey);
				String tokenid = isLoggedIn(user.getString("user_email"));
				if(tokenid != null) {
					Key userKey2 = datastore.newKeyFactory().setKind("User").newKey(tokenid);
					datastore.delete(tokenKey);
				}
				return Response.ok(g.toJson(tokenEnt)).build();
			} else {
				return Response.status(Status.BAD_REQUEST).entity("Can't remove account.").build();
			}

		}

		return Response.status(Status.BAD_REQUEST).entity("Not logged in...").build();
	}



	@POST
	@Path("/listusers")
	@Produces(MediaType.APPLICATION_JSON)
	public Response listUsers(@HeaderParam("Authorization") String tokenId) {
		LOG.fine("Attempt to list users: " + tokenId);

		Key tokenKey = datastore.newKeyFactory().setKind("Token").newKey(tokenId);
		Entity tokenEnt = datastore.get(tokenKey);

		if (tokenEnt == null) {
			return Response.status(Status.UNAUTHORIZED).entity("Not logged in...").build();
		}

		String tokenRole = tokenEnt.getString("token_role");
		List<Map<String, String>> userList = new ArrayList<>();

		Query<Entity> query = Query.newEntityQueryBuilder().setKind("User").build();
		QueryResults<Entity> users = datastore.run(query);

		users.forEachRemaining(user -> {
			Map<String, String> userInfo = new HashMap<>();
			String userRole = user.getString("user_role");
			String accountState = user.getString("user_accountstate");
			String privacy = user.getString("user_privacy");

			if (tokenRole.equals("enduser")) {
				if (userRole.equals("enduser") && privacy.equals("público") && accountState.equals("ativada")) {
					userInfo.put("username", user.getString("user_user_name"));
					userInfo.put("email", user.getString("user_email"));
					userInfo.put("name", user.getString("user_full_name"));
					userList.add(userInfo);
				}
			} else if (tokenRole.equals("backoffice")) {
				if (userRole.equals("enduser")) {
					userInfo.put("username", user.getString("user_user_name"));
					userInfo.put("email", user.getString("user_email"));
					userInfo.put("name", user.getString("user_full_name"));
					userInfo.put("phoneNumber", user.getString("user_phone_number"));
					userInfo.put("privacy", user.getString("user_privacy"));
					userInfo.put("accountState", user.getString("user_accountstate"));
					userInfo.put("ccNumber", user.contains("user_ccnumber") ? user.getString("user_ccnumber") : "NOT DEFINED");
					userInfo.put("nif", user.contains("user_nif") ? user.getString("user_nif") : "NOT DEFINED");
					userInfo.put("employer", user.contains("user_employer") ? user.getString("user_employer") : "NOT DEFINED");
					userInfo.put("address", user.contains("user_address") ? user.getString("user_address") : "NOT DEFINED");
					userInfo.put("employerNif", user.contains("user_employernif") ? user.getString("user_employernif") : "NOT DEFINED");
					userList.add(userInfo);
				}
			} else if (tokenRole.equals("admin")) {
				userInfo.put("username", user.getString("user_user_name"));
				userInfo.put("email", user.getString("user_email"));
				userInfo.put("name", user.getString("user_full_name"));
				userInfo.put("role", user.getString("user_role"));
				userInfo.put("phoneNumber", user.getString("user_phone_number"));
				userInfo.put("privacy", user.getString("user_privacy"));
				userInfo.put("accountState", user.getString("user_accountstate"));
				userInfo.put("ccNumber", user.contains("user_ccnumber") ? user.getString("user_ccnumber") : "NOT DEFINED");
				userInfo.put("nif", user.contains("user_nif") ? user.getString("user_nif") : "NOT DEFINED");
				userInfo.put("employer", user.contains("user_employer") ? user.getString("user_employer") : "NOT DEFINED");
				userInfo.put("address", user.contains("user_address") ? user.getString("user_address") : "NOT DEFINED");
				userInfo.put("employerNif", user.contains("user_employernif") ? user.getString("user_employernif") : "NOT DEFINED");
				userList.add(userInfo);
			}
		});

		return Response.ok(g.toJson(userList)).build();
	}

	@POST
	@Path("/changeattributes")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response changeAttributes(@HeaderParam("Authorization") String tokenId, ChangeAttributesData data) {
		LOG.fine("Attempt to change attributes: " + tokenId);

		Key tokenKey = datastore.newKeyFactory().setKind("Token").newKey(tokenId);
		Entity tokenEnt = datastore.get(tokenKey);

		if (tokenEnt == null) {
			return Response.status(Status.UNAUTHORIZED).entity("Not logged in...").build();
		}

		String loggedInEmail = tokenEnt.getString("token_userid");
		String loggedInRole = tokenEnt.getString("token_role");

		Key userKey = datastore.newKeyFactory().setKind("User").newKey(data.emailOfChange);
		Entity user = datastore.get(userKey);

		if(user == null) {
			return Response.status(Status.UNAUTHORIZED).entity("Can't change attributes of a user that does not exist...").build();
		}

		Entity.Builder userBuilder = Entity.newBuilder(userKey);

		if(loggedInRole.equals("enduser")) {
			if (!data.emailOfChange.equals(loggedInEmail))
				return Response.status(Status.UNAUTHORIZED).entity("Endusers can't change others attributes...").build();
			if (!user.getString("user_accountstate").equals("ativada"))
				return Response.status(Status.UNAUTHORIZED).entity("Account not active").build();
			if (data.role != null || data.accountState != null || data.username != null || data.email != null)
				return Response.status(Status.UNAUTHORIZED).entity("Can't change neither your username, email, role nor state...").build();

			userBuilder.set("user_email", user.getString("user_email"));
			userBuilder.set("user_user_name", user.getString("user_user_name"));
			userBuilder.set("user_full_name", user.getString("user_full_name"));
			userBuilder.set("user_role", user.getString("user_role"));
			userBuilder.set("user_accountstate", user.getString("user_accountstate"));

			if(data.phoneNumber != null) {
				if (!data.phoneNumber.matches("^\\+[0-9]+$"))
					return Response.status(Status.UNAUTHORIZED).entity("Invalid phone number").build();
				userBuilder.set("user_phone_number", data.phoneNumber);
			} else {
				userBuilder.set("user_phone_number", user.getString("user_phone_number"));
			}
			if(data.privacy != null) {
				userBuilder.set("user_privacy", data.privacy);
			} else {
				userBuilder.set("user_privacy", user.getString("user_privacy"));
			}
			if(data.password != null) {
				userBuilder.set("user_pwd", data.password);
			} else {
				userBuilder.set("user_pwd", user.getString("user_pwd"));
			}
			if(data.job != null) {
				userBuilder.set("user_job", data.job);
			} else if(user.contains("user_job")) {
				userBuilder.set("user_job", user.getString("user_job"));
			}

			if (data.ccNumber != null && data.ccNumber.matches("\\d+")) {
				userBuilder.set("user_ccNumber", data.ccNumber);
			} else if(user.contains("user_ccNumber")) {
				userBuilder.set("user_ccnumber", user.getString("user_ccnumber"));
			}
			if (data.nif != null) {
				userBuilder.set("user_nif", data.nif);
			} else if(user.contains("user_nif")) {
				userBuilder.set("user_nif", user.getString("user_nif"));
			}
			if (data.employer != null) {
				userBuilder.set("user_employer", data.employer);
			} else if(user.contains("user_employer")) {
				userBuilder.set("user_employer", user.getString("user_employer"));
			}
			if (data.address != null) {
				userBuilder.set("user_address", data.address);
			} else if(user.contains("user_address")) {
				userBuilder.set("user_address", user.getString("user_address"));
			}
			if (data.employerNif != null && data.employerNif.matches("\\d+")) {
				userBuilder.set("user_employernif", data.employerNif);
			} else if(user.contains("user_employernif")) {
				userBuilder.set("user_employernif", user.getString("user_employernif"));
			}

			user = userBuilder.build();
			datastore.update(user);
			return Response.ok("You successfully changed your attributes!").build();
		}
		if(loggedInRole.equals("backoffice")) {
			if(!(data.role != null && data.role.equals("enduser")))
				return Response.status(Status.UNAUTHORIZED).entity("Can only change endusers attributes...").build();
			if(data.email != null || data.username != null)
				return Response.status(Status.UNAUTHORIZED).entity("Can't change neither username nor email...").build();

			userBuilder.set("user_email", user.getString("user_email"));
			userBuilder.set("user_user_name", user.getString("user_user_name"));

			if(data.role != null) {
				if(!(data.role.equals("admin") || data.role.equals("enduser") || data.role.equals("partner") || data.role.equals("backoffice")))
					return Response.status(Status.BAD_REQUEST).entity("Invalid role.").build();
				if(data.role.equals("admin")) {
					userBuilder.set("user_role", data.role);
					userBuilder.set("user_accountstate", "ativada");
				}
				if(data.role.equals("partner") || data.role.equals("backoffice"))
					userBuilder.set("user_role", data.role);
			} else {
				userBuilder.set("user_role", user.getString("user_role"));
			}
			if(data.phoneNumber != null) {
				if (!data.phoneNumber.matches("^\\+[0-9]+$"))
					return Response.status(Status.UNAUTHORIZED).entity("Invalid phone number").build();
				userBuilder.set("user_phone_number", data.phoneNumber);
			} else {
				userBuilder.set("user_phone_number", user.getString("user_phone_number"));
			}
			if(data.privacy != null) {
				userBuilder.set("user_privacy", data.privacy);
			} else {
				userBuilder.set("user_privacy", user.getString("user_privacy"));
			}
			if(data.job != null) {
				userBuilder.set("user_job", data.job);
			} else if(user.contains("user_job")) {
				userBuilder.set("user_job", user.getString("user_job"));
			}

			if (data.ccNumber != null && data.ccNumber.matches("\\d+")) {
				userBuilder.set("user_ccNumber", data.ccNumber);
			} else if(user.contains("user_ccNumber")) {
				userBuilder.set("user_ccnumber", user.getString("user_ccnumber"));
			}
			if (data.nif != null) {
				userBuilder.set("user_nif", data.nif);
			} else if(user.contains("user_nif")) {
				userBuilder.set("user_nif", user.getString("user_nif"));
			}
			if (data.employer != null) {
				userBuilder.set("user_employer", data.employer);
			} else if(user.contains("user_employer")) {
				userBuilder.set("user_employer", user.getString("user_employer"));
			}
			if (data.address != null) {
				userBuilder.set("user_address", data.address);
			} else if(user.contains("user_address")) {
				userBuilder.set("user_address", user.getString("user_address"));
			}
			if (data.employerNif != null && data.employerNif.matches("\\d+")) {
				userBuilder.set("user_employernif", data.employerNif);
			} else if(user.contains("user_employernif")) {
				userBuilder.set("user_employernif", user.getString("user_employernif"));
			}

			user = userBuilder.build();
			datastore.update(user);
			return Response.ok("You successfully changed your attributes!").build();
		}
		if(loggedInRole.equals("admin")) {
			if(data.email != null) {
				if (!data.email.contains("@"))
					return Response.status(Status.UNAUTHORIZED).entity("Invalid email address").build();
				userBuilder.set("user_email", data.email);
			} else {
				userBuilder.set("user_email", user.getString("user_email"));
			}
			if(data.username != null) {
				userBuilder.set("user_user_name", data.username);
			} else {
				userBuilder.set("user_user_name", user.getString("user_user_name"));
			}
			if(data.name != null) {
				userBuilder.set("user_full_name", data.name);
			} else {
				userBuilder.set("user_full_name", user.getString("user_full_name"));
			}
			if(data.accountState != null) {
				userBuilder.set("user_accountstate", data.accountState);
			} else {
				userBuilder.set("user_accountstate", user.getString("user_accountstate"));
			}
			if(data.role != null) {
				if(!(data.role.equals("admin") || data.role.equals("enduser") || data.role.equals("partner") || data.role.equals("backoffice")))
					return Response.status(Status.BAD_REQUEST).entity("Invalid role.").build();
				if(data.role.equals("admin")) {
					userBuilder.set("user_role", data.role);
					userBuilder.set("user_accountstate", "ativada");
				}
				if(data.role.equals("partner") || data.role.equals("backoffice"))
					userBuilder.set("user_role", data.role);
			} else {
				userBuilder.set("user_role", user.getString("user_role"));
			}
			if(data.phoneNumber != null) {
				if (!data.phoneNumber.matches("^\\+[0-9]+$"))
					return Response.status(Status.UNAUTHORIZED).entity("Invalid phone number").build();
				userBuilder.set("user_phone_number", data.phoneNumber);
			} else {
				userBuilder.set("user_phone_number", user.getString("user_phone_number"));
			}
			if(data.privacy != null) {
				userBuilder.set("user_privacy", data.privacy);
			} else {
				userBuilder.set("user_privacy", user.getString("user_privacy"));
			}


			if(data.password != null) {
				userBuilder.set("user_pwd", data.password);
			} else {
				userBuilder.set("user_pwd", user.getString("user_pwd"));
			}

			if(data.job != null) {
				userBuilder.set("user_job", data.job);
			} else if(user.contains("user_job")) {
				userBuilder.set("user_job", user.getString("user_job"));
			}

			if (data.ccNumber != null && data.ccNumber.matches("\\d+")) {
				userBuilder.set("user_ccNumber", data.ccNumber);
			} else if(user.contains("user_ccNumber")) {
				userBuilder.set("user_ccnumber", user.getString("user_ccnumber"));
			}
			if (data.nif != null) {
				userBuilder.set("user_nif", data.nif);
			} else if(user.contains("user_nif")) {
				userBuilder.set("user_nif", user.getString("user_nif"));
			}
			if (data.employer != null) {
				userBuilder.set("user_employer", data.employer);
			} else if(user.contains("user_employer")) {
				userBuilder.set("user_employer", user.getString("user_employer"));
			}
			if (data.address != null) {
				userBuilder.set("user_address", data.address);
			} else if(user.contains("user_address")) {
				userBuilder.set("user_address", user.getString("user_address"));
			}
			if (data.employerNif != null && data.employerNif.matches("\\d+")) {
				userBuilder.set("user_employernif", data.employerNif);
			} else if(user.contains("user_employernif")) {
				userBuilder.set("user_employernif", user.getString("user_employernif"));
			}



			user = userBuilder.build();
			datastore.update(user);
			return Response.ok("You successfully changed your attributes!").build();


		}
		return Response.status(Status.BAD_REQUEST).entity("Partner can't change attributes...").build();
	}

	@POST
	@Path("/changepassword")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response changeAttributes(@HeaderParam("Authorization") String tokenId, LoginData data) {
		return Response.status(Status.UNAUTHORIZED).entity("Unauthorized").build();
	}




	private String isLoggedIn(String email) {
		Query<Entity> query = Query.newEntityQueryBuilder().setKind("Token").build();
		QueryResults<Entity> loggers = datastore.run(query);

		final String[] tokenid = {null};

		loggers.forEachRemaining(user -> {
			if(user.getString("token_userid").equals(email)) {
				tokenid[0] = user.getString("token_id");
			}
		});
		return tokenid[0];
	}


}
