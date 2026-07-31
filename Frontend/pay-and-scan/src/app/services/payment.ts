import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface CreatePaymentRequest {
    amount: number;
}

export interface CreatePaymentResponse {
    id: string;
    qrCodeBase64: string;
    status: string;
}

export interface PaymentStatusResponse {
    id: string;
    status: string;
}

@Injectable({
  providedIn: 'root'
})
export class PaymentService {
    private http = inject(HttpClient);
    private baseUrl = 'http://localhost:8080/api/payments';

    createPayment(request: CreatePaymentRequest): Observable<CreatePaymentResponse> {
        return this.http.post<CreatePaymentResponse>(`${this.baseUrl}`, request);
    }

    getPaymentStatus(paymentId: string): Observable<PaymentStatusResponse> {
        return this.http.get<PaymentStatusResponse>(`${this.baseUrl}/status`,
            {
                params: { paymentId }
            }
        );
    }

}
