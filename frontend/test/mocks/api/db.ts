import * as json from '~/domain/json';
import type {DomainObject, Id, IdKey} from '~/domain/objects';

class Table<T extends DomainObject> {
  private rows: T[] = [];
  private nextId = 1;

  clear() {
    this.nextId = 1;
    this.rows = [];
  }

  findAll(predicate: (row: T) => boolean = () => true) {
    return this.rows.filter(predicate);
  }

  get({id}: T['id']) {
    return this.rows.find(row => row.id.id === id) ?? null;
  }

  update(object: T) {
    const index = this.rows.findIndex(row => row.id.id === object.id.id);
    if (index === -1) return false;
    this.rows[index] = object;
    return true;
  }

  delete({id}: T['id']) {
    const index = this.rows.findIndex(row => row.id.id === id);
    if (index >= 0) {
      this.rows.splice(index, 1);
      return true;
    }
    return false;
  }

  insert(domainObject: T) {
    const id = this.nextId++;
    domainObject.id.id = id;
    this.rows.push(domainObject);
    return id;
  }
}

export class JSONTable<T extends DomainObject> {
  private table: Table<T>;

  constructor(private parserPair: json.JSONParserPair<T>) {
    this.table = new Table();
  }

  clear() {
    this.table.clear();
  }

  findAll(predicate?: (row: T) => boolean) {
    return this.table.findAll(predicate).map(this.parserPair.unparse);
  }

  findMatching<
    K extends IdKey<T>,
    I extends string = T[K] extends Id<infer U> ? U : never,
  >(key: K, id: json.JSONDomainObject<DomainObject<I>>['id']) {
    return this.findAll(row => (row[key] as Id<I>).id === id);
  }

  get(id: json.JSONDomainObject<T>['id']) {
    const row = this.table.get(this.parserPair.field('id').parse(id));
    return row ? this.parserPair.unparse(row) : null;
  }

  update(object: json.JSONDomainObject<T>) {
    return this.table.update(this.parserPair.parse(object));
  }

  delete(id: json.JSONDomainObject<T>['id']) {
    return this.table.delete(this.parserPair.field('id').parse(id));
  }

  insert(domainObject: json.JSONDomainObject<T>) {
    return this.table.insert(this.parserPair.parse(domainObject));
  }
}
