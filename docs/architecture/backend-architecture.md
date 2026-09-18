# Backend Architecture

## Purpose
The backend serves the Loc application API and owns product and user behaviour.

## Responsibilities
- authentication and authorization
- user accounts
- event discovery API
- user interactions with events
- admin behaviour
- comments / communication features
- application-owned state

## Event Catalog
- the data application publishes the event catalog
- the backend reads and exposes that catalog
- the backend does not collect or normalize external event sources
- shared catalog structures are contracts between data and backend

## Data Ownership

Data application owns:
- external collection
- normalization
- enrichment
- catalog publication

Backend owns:
- users
- authentication
- saved / going state
- comments
- notifications
- admin/product state

## User Behaviour

Users can:
- discover events
- view event details
- interact with events
- manage their own application state

Admins can:
- perform administrative actions on platform content
- manage backend-owned platform behaviour

## Architecture Boundary

External sources
↓
Data Application
↓
Published Event Catalog
↓
Backend API
↓
Frontend / Users

## Deferred Decisions
Future backend features and implementation changes are designed only when they become active work.