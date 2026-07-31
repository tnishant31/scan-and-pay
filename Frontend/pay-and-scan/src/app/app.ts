import { Component, AfterViewInit, ChangeDetectorRef, inject, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { interval, Subscription, switchMap } from 'rxjs';
import { environment } from '../environments/environment';

declare const google: any;

export interface User {
  name: string;
  email: string;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements AfterViewInit, OnDestroy {

  page: 'SIGN_IN' | 'SIGN_UP' | 'PAYMENT' = 'SIGN_IN';
  currentUser: User | null = null;

  // Sign In / Sign Up fields
  signInEmail = '';
  signUpName = '';
  signUpEmail = '';
  signUpOtp = '';
  otpSent = false;

  // Payment fields
  amount: number | null = null;
  paymentId = '';
  qrCodeBase64 = '';
  step: 'INPUT' | 'QR' | 'PROCESSING' | 'SUCCESS' | 'CANCELLED' | 'EXPIRED' = 'INPUT';
  processingMessage = '';

  // Timer & Polling Subscriptions
  timerSeconds = 180;
  private timerSubscription?: Subscription;
  private pollSubscription?: Subscription;

  private http = inject(HttpClient);
  private cdr = inject(ChangeDetectorRef);

  ngAfterViewInit(): void {
    this.initGoogleAuth();
  }

  // Initialize Google SDK and render standard buttons
  private initGoogleAuth(): void {
    if (typeof google !== 'undefined' && google.accounts && google.accounts.id) {
      google.accounts.id.initialize({
        // REPLACE WITH YOUR REAL GOOGLE CLIENT ID FROM GOOGLE CLOUD CONSOLE
        client_id: '677968139288-v2cc94njcpjrcsuk6oaeh9egh18umpt9.apps.googleusercontent.com',
        callback: (response: any) => this.handleGoogleCredential(response)
      });

      this.renderGoogleButtons();
    } else {
      // Retry if Google SDK script hasn't loaded yet
      setTimeout(() => this.initGoogleAuth(), 500);
    }
  }

  renderGoogleButtons(): void {
    const btnSignIn = document.getElementById('google-btn-signin');
    if (btnSignIn) {
      btnSignIn.innerHTML = ''; // Clear prior renders
      google.accounts.id.renderButton(btnSignIn, {
        theme: 'outline',
        size: 'large',
        width: '100%',
        text: 'signin_with'
      });
    }

    const btnSignUp = document.getElementById('google-btn-signup');
    if (btnSignUp) {
      btnSignUp.innerHTML = ''; // Clear prior renders
      google.accounts.id.renderButton(btnSignUp, {
        theme: 'outline',
        size: 'large',
        width: '100%',
        text: 'signup_with'
      });
    }
  }

  private handleGoogleCredential(response: any): void {
    if (response && response.credential) {
      // Decode JWT token payload from Google response
      const payload = JSON.parse(atob(response.credential.split('.')[1]));
      this.authenticateGoogleUser(payload.email, payload.name);
    }
  }

  private authenticateGoogleUser(email: string, name: string): void {
    const cleanEmail = email.trim().toLowerCase();
    this.http.post<User>('${environment.apiUrl}/auth/google-register', {
      name: name,
      email: cleanEmail
    }).subscribe({
      next: (user) => {
        this.currentUser = user;
        this.page = 'PAYMENT';
        this.cdr.detectChanges();
      },
      error: (err) => alert('Google authentication failed. Please try again.')
    });
  }

  // Standard Email Auth
  signIn(): void {
    if (!this.signInEmail) {
      alert('Please enter your email');
      return;
    }
    const cleanEmail = this.signInEmail.trim().toLowerCase();
    this.http.post<User>('${environment.apiUrl}/auth/signin', { email: cleanEmail })
      .subscribe({
        next: (user) => {
          this.currentUser = user;
          this.page = 'PAYMENT';
          this.cdr.detectChanges();
        },
        error: () => alert('User not found! Please Sign Up first.')
      });
  }

  sendSignUpOtp(): void {
    if (!this.signUpEmail || !this.signUpName) {
      alert('Please enter Name and Email');
      return;
    }
    const cleanEmail = this.signUpEmail.trim().toLowerCase();
    this.http.post('${environment.apiUrl}/auth/send/sign-up/otp', { email: cleanEmail })
      .subscribe({
        next: () => {
          this.otpSent = true;
          alert('OTP sent to your email!');
          this.cdr.detectChanges();
        },
        error: () => alert('Failed to send OTP. Check backend logs.')
      });
  }

  completeSignUp(): void {
    if (!this.signUpOtp) {
      alert('Please enter the OTP');
      return;
    }
    const cleanEmail = this.signUpEmail.trim().toLowerCase();
    const cleanOtp = this.signUpOtp.trim();

    this.http.post<User>('${environment.apiUrl}/auth/signup', {
      name: this.signUpName.trim(),
      email: cleanEmail,
      otp: cleanOtp
    }).subscribe({
      next: () => {
        alert('Registration successful! Please Sign In.');
        this.page = 'SIGN_IN';
        this.signInEmail = cleanEmail;
        this.otpSent = false;
        this.signUpOtp = '';
        this.cdr.detectChanges();
        setTimeout(() => this.renderGoogleButtons(), 100);
      },
      error: () => alert('Invalid or expired OTP!')
    });
  }

  switchPage(newPage: 'SIGN_IN' | 'SIGN_UP'): void {
    this.page = newPage;
    this.cdr.detectChanges();
    setTimeout(() => this.renderGoogleButtons(), 100);
  }

  logOff(): void {
    this.currentUser = null;
    this.page = 'SIGN_IN';
    this.signInEmail = '';
    this.resetPaymentState();
    this.cdr.detectChanges();
    setTimeout(() => this.renderGoogleButtons(), 100);
  }

  // Payment Flow
  payViaUpi(): void {
    if (this.amount === null || this.amount <= 0) {
      alert('Please enter a valid amount');
      return;
    }

    const currentAmount = this.amount;
    this.resetSubscriptions();

    this.http.post<any>('${environment.apiUrl}/payments', {
      amount: currentAmount,
      userEmail: this.currentUser?.email
    })
      .subscribe({
        next: (res) => {
          this.amount = currentAmount;
          this.paymentId = res.id;
          this.qrCodeBase64 = res.qrCodeBase64.startsWith('data:')
            ? res.qrCodeBase64
            : 'data:image/png;base64,' + res.qrCodeBase64;

          this.step = 'QR';
          this.cdr.detectChanges();

          this.startExpiryTimer();
          this.startPolling();
        }
      });
  }

  private startExpiryTimer(): void {
    this.timerSeconds = 180;
    this.timerSubscription = interval(1000).subscribe(() => {
      this.timerSeconds--;
      this.cdr.detectChanges();

      if (this.timerSeconds <= 0) {
        this.resetSubscriptions();
        this.step = 'EXPIRED';
        this.cdr.detectChanges();
      }
    });
  }

  startPolling(): void {
    this.pollSubscription?.unsubscribe();
    
    this.pollSubscription = interval(1500).pipe(
      switchMap(() => this.http.get<any>(`${environment.apiUrl}/payments/status?paymentId=${this.paymentId}`))
    ).subscribe({
      next: (res) => {
        console.log('Payment status update from backend:', res); // Log to browser dev tools console

        if (!res || !res.status) return;

        const currentStatus = res.status.toString().trim().toUpperCase();

        if (currentStatus === 'SCANNED_PROCESSING' || currentStatus === 'FETCHING_BANK_INFO') {
          this.step = 'PROCESSING';
          this.processingMessage = 'Awaiting payment verification on phone...';
          this.cdr.detectChanges(); // Force UI update
        } 
        else if (currentStatus === 'COMPLETED') {
          console.log('Payment marked completed! Updating UI to SUCCESS.');
          this.resetSubscriptions();
          this.step = 'SUCCESS';
          this.cdr.detectChanges(); // Force UI update to show success screen
        } 
        else if (currentStatus === 'FAILED') {
          this.resetSubscriptions();
          this.step = 'CANCELLED';
          this.cdr.detectChanges(); // Force UI update
        }
      },
      error: (err) => {
        console.error('Error polling payment status:', err);
      }
    });
  }

  resetPaymentState(): void {
    this.resetSubscriptions();
    this.amount = null;
    this.paymentId = '';
    this.qrCodeBase64 = '';
    this.step = 'INPUT';
    this.cdr.detectChanges();
  }

  private resetSubscriptions(): void {
    this.timerSubscription?.unsubscribe();
    this.pollSubscription?.unsubscribe();
  }

  ngOnDestroy(): void {
    this.resetSubscriptions();
  }
}