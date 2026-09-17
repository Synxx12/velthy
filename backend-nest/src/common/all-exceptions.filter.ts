/**
 * Renders a `PartyError` as the JSON body the client expects, and anything else
 * as a plain 500 rather than an HTML error page.
 *
 * One filter for the whole app so the error shape is decided in one place: the
 * Android client parses `{ error, message }`, and a framework default that
 * returned `{ statusCode, message, error }` on some paths and this on others is
 * two shapes for one contract.
 */
import { ArgumentsHost, Catch, ExceptionFilter, HttpException, HttpStatus, Logger } from '@nestjs/common';
import type { Response } from 'express';

import { PartyError } from '../party/party.js';

@Catch()
export class AllExceptionsFilter implements ExceptionFilter {
  private readonly log = new Logger('Errors');

  catch(exception: unknown, host: ArgumentsHost): void {
    const response = host.switchToHttp().getResponse<Response>();
    // A WebSocket upgrade passes through here too, and has no response object.
    if (typeof response?.status !== 'function') return;

    if (exception instanceof PartyError) {
      response.status(exception.status).json({ error: exception.code, message: exception.message });
      return;
    }

    if (exception instanceof HttpException) {
      const status = exception.getStatus();
      if (status === HttpStatus.NOT_FOUND) {
        response.status(404).json({ error: 'not_found', message: 'No such route.' });
        return;
      }
      const body = exception.getResponse();
      response.status(status).json(
        typeof body === 'string' ? { error: 'http_error', message: body } : { error: 'http_error', ...body },
      );
      return;
    }

    // Anything unexpected is a bug, and a bug with a stack in the log and a
    // plain 500 on the wire is worth more than one leaked to the client.
    this.log.error('unhandled error', exception instanceof Error ? exception.stack : String(exception));
    response.status(500).json({ error: 'internal_error', message: 'Something went wrong.' });
  }
}
