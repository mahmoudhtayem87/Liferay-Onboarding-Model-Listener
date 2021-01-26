# Liferay-Onboarding-Model-Listener

Liferay Onboarding Model Listener has been created as an example of on boarding process and how it can be implemented using a simple on boarding form with a model listener which will create the user and add it to a specific user role upon the request approval.

In order to use the repo please note the following:
•	This model listener requires initial configuration which can be found on:
system settings -> platform -> third party -> “form-user-account-listener-config-name”
o	email-address-field: this is a required field where you should mention the form field id which holds the email id for the “to be created” user
o	First Name: this is a required field where you should mention the form field id which holds the first name for the “to be created” user
o	Middle Name: this is a required field where you should mention the form field id which holds the middle name for the “to be created” user
o	Last Name: this is a required field where you should mention the form field id which holds the last name for the “to be created” user
o	Role-id: this is a required field where you should mention the role id which will be assigned to the “to be created” user or the creator user “the one who submit the form”.
o	Create-user: if you enable this option, it means that once the form is approved a new user will be created based on the information has been passed in the form.
o	Add-user-to-role: if you enable this option, it means that once the form is approved a “creator / new created” user should be added to role.
o	form-user-account-listener-form-id: this is where you specify the onboarding form id, where this model listener will be listening. 
•	This model listener does not check if the user has been created before or not.
•	If you faced the following error: 
The activate method has thrown an exception java.lang.RuntimeException: Unable to create snapshot class for interface com.liferay.onboarding.model.listener.OnboardingModelListenerConfiguration
Then please make sure to visit “system settings -> platform -> third party -> “form-user-account-listener-config-name” and make sure to save the configuration.




