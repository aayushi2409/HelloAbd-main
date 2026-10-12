package com.example.hello_abd.ui.theme.screens

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.hello_abd.MainActivity
import com.example.hello_abd.R
import com.example.hello_abd.repository.UserProfile
import com.example.hello_abd.ui.theme.HelloAbdTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class EmailPasswordActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            HelloAbdTheme {
                LoginScreen(
                    onSignIn = { email, password, callback -> signIn(email, password, callback) },
                    onCreateAccount = { email, password, callback -> createAccount(email, password, callback) },
                    onGoogleSignIn = { googleSignIn() }
                )
            }
        }
    }

    private fun googleSignIn() {
        val signInIntent = googleSignInClient.signInIntent
        googleLauncher.launch(signInIntent)
    }

    private val googleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            Log.w(TAG, "Google sign in failed", e)
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        // Ensure profile exists in Firestore
                        lifecycleScope.launch {
                            val doc = db.collection("users").document(user.uid).get().await()
                            if (!doc.exists()) {
                                val profile = UserProfile(
                                    email = user.email,
                                    name = user.displayName ?: user.email?.substringBefore("@")
                                )
                                db.collection("users").document(user.uid).set(profile).await()
                            }
                            updateUI(user)
                        }
                    }
                } else {
                    Log.w(TAG, "Firebase auth with Google failed", task.exception)
                }
            }
    }

    public override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            verifyUserAndNavigate(currentUser)
        }
    }

    private fun verifyUserAndNavigate(user: FirebaseUser) {
        lifecycleScope.launch {
            try {
                val doc = db.collection("users").document(user.uid).get().await()
                if (doc.exists()) {
                    updateUI(user)
                    return@launch
                }

                val query = db.collection("users").whereEqualTo("email", user.email).limit(1).get().await()
                if (!query.isEmpty) {
                    updateUI(user)
                } else {
                    auth.signOut()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error verifying user", e)
                auth.signOut()
            }
        }
    }

    private fun createAccount(email: String, password: String, callback: (Task<AuthResult>) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        val profile = UserProfile(email = email, name = email.substringBefore("@"))
                        db.collection("users").document(user.uid).set(profile)
                            .addOnCompleteListener { firestoreTask ->
                                if (firestoreTask.isSuccessful) {
                                    updateUI(user)
                                }
                                callback(task)
                            }
                    } else {
                        callback(task)
                    }
                } else {
                    callback(task)
                }
            }
    }

    private fun signIn(email: String, password: String, callback: (Task<AuthResult>) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        db.collection("users").document(user.uid).get()
                            .addOnCompleteListener { docTask ->
                                if (docTask.isSuccessful && docTask.result?.exists() == true) {
                                    updateUI(user)
                                    callback(task)
                                } else {
                                    db.collection("users").whereEqualTo("email", user.email).limit(1).get()
                                        .addOnCompleteListener { queryTask ->
                                            if (queryTask.isSuccessful && !queryTask.result.isEmpty) {
                                                updateUI(user)
                                                callback(task)
                                            } else {
                                                auth.signOut()
                                                val exception = Exception("Account record not found in system.")
                                                callback(Tasks.forException(exception))
                                            }
                                        }
                                }
                            }
                    } else {
                        callback(task)
                    }
                } else {
                    callback(task)
                }
            }
    }

    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    companion object {
        private const val TAG = "EmailPassword"
    }
}

@Composable
fun LoginScreen(
    onSignIn: (String, String, (Task<AuthResult>) -> Unit) -> Unit,
    onCreateAccount: (String, String, (Task<AuthResult>) -> Unit) -> Unit,
    onGoogleSignIn: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSignIn by remember { mutableStateOf(true) }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val primaryColor = Color(0xFF1976D2) // Professional Blue
    val accentColor = Color(0xFF00B8D4) // Brighter Cyan for better visibility

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        cursorColor = accentColor,
        focusedBorderColor = accentColor,
        unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
        focusedLabelColor = accentColor,
        unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF121212), Color(0xFF1F1F1F))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Professional Logo / Icon Placeholder
            Surface(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(20.dp),
                color = primaryColor.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, primaryColor)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "AI",
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "VIRTUAL ASSISTANT",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 4.sp,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(bottom = 40.dp)
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMessage = null },
                label = { Text("Email Address") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                colors = textFieldColors,
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMessage = null },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
                    }
                },
                colors = textFieldColors,
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            )

            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = TextStyle(fontSize = 12.sp),
                    modifier = Modifier.padding(top = 12.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (isLoading) {
                CircularProgressIndicator(color = accentColor, modifier = Modifier.size(32.dp))
            } else {
                Button(
                    onClick = {
                        if (email.isBlank() || password.isBlank()) {
                            errorMessage = "Please enter both email and password credentials."
                            return@Button
                        }
                        isLoading = true
                        if (isSignIn) {
                            onSignIn(email, password) { task ->
                                isLoading = false
                                if (!task.isSuccessful) {
                                    errorMessage = when (task.exception) {
                                        is FirebaseAuthInvalidUserException -> "User account does not exist."
                                        is FirebaseAuthInvalidCredentialsException -> "Invalid authentication credentials."
                                        else -> task.exception?.message ?: "An authentication error occurred."
                                    }
                                }
                            }
                        } else {
                            onCreateAccount(email, password) { task ->
                                isLoading = false
                                if (!task.isSuccessful) {
                                    errorMessage = when (task.exception) {
                                        is FirebaseAuthUserCollisionException -> "This email address is already registered."
                                        else -> "Please ensure your email format and password meet security requirements."
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (isSignIn) "SIGN IN" else "CREATE ACCOUNT",
                        color = Color.White,
                        style = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // OR Separator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = Color.White.copy(alpha = 0.2f)
                    )
                    Text(
                        text = " OR ",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = Color.White.copy(alpha = 0.2f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Google Sign-In Button
                OutlinedButton(
                    onClick = onGoogleSignIn,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_compass), // Generic icon as placeholder
                            contentDescription = "Google Logo",
                            modifier = Modifier.size(20.dp),
                            tint = Color.Unspecified
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Continue with Google",
                            style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isSignIn) "New user?" else "Already have an account?",
                    color = Color.White.copy(alpha = 0.7f)
                )
                TextButton(onClick = { isSignIn = !isSignIn; errorMessage = null }, enabled = !isLoading) {
                    Text(
                        text = if (isSignIn) "Create Account" else "Sign In",
                        color = Color.White,
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    )
                }
            }
        }
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    device = Devices.PIXEL_5,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun LoginScreenPreview() {
    HelloAbdTheme(dynamicColor = false) {
        LoginScreen(
            onSignIn = { _, _, _ -> },
            onCreateAccount = { _, _, _ -> },
            onGoogleSignIn = {}
        )
    }
}
